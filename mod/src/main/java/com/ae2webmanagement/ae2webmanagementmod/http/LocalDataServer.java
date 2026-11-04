package com.ae2webmanagement.ae2webmanagementmod.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import com.ae2webmanagement.ae2webmanagementmod.Config;
import com.ae2webmanagement.ae2webmanagementmod.command.CommandManager;
import com.ae2webmanagement.ae2webmanagementmod.command.CraftCommand;
import com.ae2webmanagement.ae2webmanagementmod.data.GridPasswords;
import com.ae2webmanagement.ae2webmanagementmod.network.NetworkIdentity;
import com.ae2webmanagement.ae2webmanagementmod.snapshot.Snapshot;
import com.ae2webmanagement.ae2webmanagementmod.snapshot.SnapshotManager;

/**
 * 模组本地数据 API。
 *
 * <p>仅绑定回环地址。GET 只读取并发缓存；POST 只把合成意图交给
 * {@link CommandManager} 的线程安全队列，世界与 AE2 状态始终在主线程处理。
 */
public final class LocalDataServer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_REQUEST_BYTES = 64 * 1024;

    private final SnapshotManager snapshots;
    private final CommandManager commands;
    private HttpServer server;
    private ExecutorService executor;

    /** 注入线程安全的快照缓存与命令队列。 */
    public LocalDataServer(SnapshotManager snapshots, CommandManager commands) {
        this.snapshots = snapshots;
        this.commands = commands;
    }

    /**
     * 启动本机 HTTP API。
     *
     * <p>拒绝绑定非回环地址；启动失败时记录日志并清理已创建的资源，
     * 不让数据 API 阻止游戏服务器继续启动。
     */
    public void start() {
        stop();

        String bindAddress = Config.HTTP_BIND_ADDRESS.get();
        int port = Config.HTTP_PORT.get();
        InetAddress address;
        try {
            address = InetAddress.getByName(bindAddress);
        } catch (UnknownHostException exception) {
            LOGGER.error("[AE2Web] Invalid HTTP bind address: {}", bindAddress, exception);
            return;
        }

        if (!address.isLoopbackAddress()) {
            LOGGER.error("[AE2Web] Refusing to expose mod data API on non-loopback address: {}", bindAddress);
            return;
        }

        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(address, port), 0);
            this.executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ae2web-http");
                thread.setDaemon(true);
                return thread;
            });
            httpServer.setExecutor(this.executor);
            httpServer.createContext("/", this::handle);
            httpServer.start();
            this.server = httpServer;
            LOGGER.info("[AE2Web] Local data API listening on http://{}:{}", bindAddress, port);
            if (Config.SHARED_SECRET.get().isEmpty()) {
                LOGGER.warn("[AE2Web] sharedSecret is empty; local API authentication is disabled");
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("[AE2Web] Failed to start local data API on {}:{}", bindAddress, port, exception);
            stop();
        }
    }

    /** 停止 HTTP 监听并关闭请求处理线程。 */
    public void stop() {
        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
        }
        if (this.executor != null) {
            this.executor.shutdownNow();
            this.executor = null;
        }
    }

    /** 统一入口：先校验共享密钥，再按 HTTP 方法分发。 */
    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!isAuthorized(exchange)) {
                sendError(exchange, 401, "unauthorized");
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, path);
                return;
            }
            if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, path);
                return;
            }

            exchange.getResponseHeaders().set("Allow", "GET, POST");
            sendError(exchange, 405, "method_not_allowed");
        } catch (Exception exception) {
            LOGGER.error("[AE2Web] Local data API request failed: {}", exchange.getRequestURI(), exception);
            sendError(exchange, 500, "internal_error");
        } finally {
            exchange.close();
        }
    }

    /** 分发只读接口：健康检查、网络列表、单个网络快照和命令历史。 */
    private void handleGet(HttpExchange exchange, String path) throws IOException {
        if ("/health".equals(path)) {
            sendJson(exchange, 200, Map.of("ok", true));
            return;
        }
        if ("/networks".equals(path)) {
            sendJson(exchange, 200, Map.of("networks", listNetworks()));
            return;
        }

        String[] parts = path.split("/");
        if (parts.length == 4 && "networks".equals(parts[1])) {
            UUID accessId;
            try {
                accessId = UUID.fromString(parts[2]);
            } catch (IllegalArgumentException exception) {
                sendError(exchange, 400, "invalid_access_id");
                return;
            }
            if ("snapshot".equals(parts[3])) {
                sendSnapshot(exchange, accessId);
                return;
            }
            if ("commands".equals(parts[3])) {
                sendCommands(exchange, accessId);
                return;
            }
        }

        sendError(exchange, 404, "not_found");
    }

    /**
     * 处理 POST 分发：合成命令投递与网格密码校验。
     */
    private void handlePost(HttpExchange exchange, String path) throws IOException {
        String[] parts = path.split("/");
        if (parts.length != 4 || !"networks".equals(parts[1])) {
            sendError(exchange, 404, "not_found");
            return;
        }

        UUID accessId;
        try {
            accessId = UUID.fromString(parts[2]);
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_access_id");
            return;
        }

        if ("craft".equals(parts[3])) {
            handleCraft(exchange, accessId);
            return;
        }
        if ("verify-password".equals(parts[3])) {
            handleVerifyPassword(exchange, accessId);
            return;
        }

        sendError(exchange, 404, "not_found");
    }

    /**
     * 处理合成命令投递。
     *
     * <p>只校验路径、网络、JSON 结构和基本数值；解析 AEKey 和访问 AE2
     * 交给主线程的 {@link CommandManager} 处理。
     */
    private void handleCraft(HttpExchange exchange, UUID accessId) throws IOException {
        if (!this.commands.hasNetwork(accessId)) {
            sendError(exchange, 404, "network_not_found");
            return;
        }

        JsonObject body;
        try {
            body = readJsonObject(exchange);
        } catch (IOException exception) {
            sendError(exchange, 400, "invalid_json");
            return;
        }
        if (!body.has("key") || !body.has("amount")) {
            sendError(exchange, 400, "missing_key_or_amount");
            return;
        }

        long amount;
        try {
            amount = body.get("amount").getAsLong();
        } catch (RuntimeException exception) {
            sendError(exchange, 400, "invalid_amount");
            return;
        }
        if (amount <= 0) {
            sendError(exchange, 400, "invalid_amount");
            return;
        }

        CompoundTag target;
        try {
            Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, body.get("key"));
            if (!(tag instanceof CompoundTag compound) || compound.isEmpty()) {
                sendError(exchange, 400, "invalid_key");
                return;
            }
            target = compound;
        } catch (RuntimeException exception) {
            sendError(exchange, 400, "invalid_key");
            return;
        }

        Optional<CraftCommand> command = this.commands.submit(accessId, target, amount);
        if (command.isEmpty()) {
            sendError(exchange, 404, "network_not_found");
            return;
        }

        CraftCommand accepted = command.get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("commandId", accepted.id().toString());
        response.put("state", accepted.state().name());
        response.put("submittedAt", accepted.submittedAt());
        sendJson(exchange, 202, response);
    }

    /**
     * 处理网格密码校验（后端在账号「解锁网格」时转发调用）。
     *
     * <p>未设密码的网络直接 204（公开）；密码哈希取自 SnapshotManager 的
     * 镜像缓存（GUI 设密后主线程立即刷新）。网络未知 → 404。</p>
     */
    private void handleVerifyPassword(HttpExchange exchange, UUID accessId) throws IOException {
        if (!this.snapshots.hasIdentity(accessId)) {
            sendError(exchange, 404, "network_not_found");
            return;
        }

        JsonObject body;
        try {
            body = readJsonObject(exchange);
        } catch (IOException exception) {
            sendError(exchange, 400, "invalid_json");
            return;
        }
        String password;
        try {
            if (!body.has("password") || !body.get("password").isJsonPrimitive()) {
                sendError(exchange, 400, "invalid_json");
                return;
            }
            password = body.get("password").getAsString();
        } catch (RuntimeException exception) {
            sendError(exchange, 400, "invalid_json");
            return;
        }

        String hash = this.snapshots.getPasswordHash(accessId);
        if (hash.isEmpty() || GridPasswords.verify(password, hash)) {
            sendEmpty(exchange, 204);
            return;
        }
        sendError(exchange, 401, "invalid_password");
    }

    /** 读取并解析请求体；请求体必须是不超过 {@value #MAX_REQUEST_BYTES} 字节的 JSON object。 */
    private JsonObject readJsonObject(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new IOException("request body too large");
        }
        String body = new String(bytes, StandardCharsets.UTF_8);
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) {
            throw new IOException("JSON object required");
        }
        return parsed.getAsJsonObject();
    }

    /** 校验 X-Shared-Secret；共享密钥为空时允许本地开发模式。 */
    private boolean isAuthorized(HttpExchange exchange) {
        String expected = Config.SHARED_SECRET.get();
        if (expected.isEmpty()) {
            return true;
        }
        String actual = exchange.getRequestHeaders().getFirst("X-Shared-Secret");
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 组装网络列表：身份信息来自缓存，在线态和摘要来自最近快照。 */
    private List<Map<String, Object>> listNetworks() {
        List<Map<String, Object>> networks = new ArrayList<>();
        for (NetworkIdentity identity : this.snapshots.getIdentities()) {
            Optional<Snapshot> snapshot = this.snapshots.get(identity.accessId());
            Map<String, Object> network = new LinkedHashMap<>();
            network.put("accessId", identity.accessId().toString());
            network.put("displayName", identity.displayName());
            network.put("dimension", identity.controllerLevel().location().toString());
            network.put("controllerX", identity.controllerPos().getX());
            network.put("controllerY", identity.controllerPos().getY());
            network.put("controllerZ", identity.controllerPos().getZ());
            network.put("online", snapshot.map(Snapshot::online).orElse(false));
            network.put("capturedAt", snapshot.map(Snapshot::capturedAt).orElse(0L));
            network.put("inventoryEntries", snapshot.map(value -> value.inventory().size()).orElse(0));
            network.put("cpuCount", snapshot.map(value -> value.craftingCpus().size()).orElse(0));
            network.put("hasPassword", !this.snapshots.getPasswordHash(identity.accessId()).isEmpty());
            networks.add(network);
        }
        return networks;
    }

    /** 返回单个网络的库存与合成 CPU 快照。 */
    private void sendSnapshot(HttpExchange exchange, UUID accessId) throws IOException {
        Optional<Snapshot> snapshot = this.snapshots.get(accessId);
        if (snapshot.isEmpty()) {
            sendError(exchange, 404, "snapshot_not_found");
            return;
        }

        Snapshot value = snapshot.get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accessId", value.accessId().toString());
        response.put("capturedAt", value.capturedAt());
        response.put("online", value.online());

        List<Map<String, Object>> inventory = stacksToJson(value.inventory());
        response.put("inventory", inventory);

        List<Map<String, Object>> cpus = new ArrayList<>();
        for (Snapshot.CpuEntry cpu : value.craftingCpus()) {
            Map<String, Object> cpuJson = new LinkedHashMap<>();
            cpuJson.put("name", cpu.name());
            cpuJson.put("busy", cpu.busy());
            cpuJson.put("availableStorage", cpu.availableStorage());
            cpuJson.put("coProcessors", cpu.coProcessors());
            cpus.add(cpuJson);
        }
        response.put("craftingCpus", cpus);
        response.put("patterns", patternsToJson(value.patterns()));

        sendJson(exchange, 200, response);
    }

    /** 将样板摘要转换成 HTTP JSON；primaryOutput/outputs/inputs 都复用 AEKey 的 JSON 表示。 */
    private static List<Map<String, Object>> patternsToJson(List<Snapshot.PatternEntry> patterns) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Snapshot.PatternEntry pattern : patterns) {
            List<Map<String, Object>> inputs = new ArrayList<>();
            for (Snapshot.PatternEntry.PatternInputEntry input : pattern.inputs()) {
                inputs.add(Map.of(
                        "possibleInputs", stacksToJson(input.possibleInputs()),
                        "multiplier", input.multiplier()));
            }

            result.add(Map.of(
                    "primaryOutput", stackToJson(pattern.primaryOutput()),
                    "outputs", stacksToJson(pattern.outputs()),
                    "inputs", inputs));
        }
        return result;
    }

    /** 将一条库存/样板条目转换成 HTTP JSON。 */
    private static Map<String, Object> stackToJson(Snapshot.StackEntry stack) {
        return Map.of(
                "key", toJson(stack.key()),
                "amount", stack.amount());
    }

    /** 将一组库存/样板条目转换成 HTTP JSON。 */
    private static List<Map<String, Object>> stacksToJson(List<Snapshot.StackEntry> stacks) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Snapshot.StackEntry stack : stacks) {
            result.add(Map.of(
                    "key", toJson(stack.key()),
                    "amount", stack.amount()));
        }
        return result;
    }

    /** 返回单个网络的全部命令历史和当前状态。 */
    private void sendCommands(HttpExchange exchange, UUID accessId) throws IOException {
        if (!this.commands.hasNetwork(accessId)) {
            sendError(exchange, 404, "network_not_found");
            return;
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (CraftCommand command : this.commands.getCommands(accessId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("commandId", command.id().toString());
            item.put("accessId", command.accessId().toString());
            item.put("state", command.state().name());
            item.put("submittedAt", command.submittedAt());
            item.put("result", command.result());
            item.put("amount", command.amount());
            item.put("target", toJson(command.target()));
            response.add(item);
        }
        sendJson(exchange, 200, Map.of("commands", response));
    }

    /** 将 AEKey 的通用 NBT 表示转换为 JSON；转换失败时退回 SNBT 字符串。 */
    private static JsonElement toJson(CompoundTag tag) {
        try {
            return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
        } catch (RuntimeException exception) {
            return new JsonPrimitive(tag.toString());
        }
    }

    /** 发送统一格式的 JSON 错误响应。 */
    private static void sendError(HttpExchange exchange, int status, String error) throws IOException {
        sendJson(exchange, status, Map.of("error", error));
    }

    /** 发送无响应体的状态码（如 204）。 */
    private static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    /** 写入 JSON 响应头和 UTF-8 响应体，并禁止缓存。 */
    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
