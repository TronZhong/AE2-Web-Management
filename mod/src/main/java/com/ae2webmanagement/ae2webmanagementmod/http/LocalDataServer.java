package com.ae2webmanagement.ae2webmanagementmod.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.ae2webmanagement.ae2webmanagementmod.account.AccountManager;
import com.ae2webmanagement.ae2webmanagementmod.command.CommandManager;
import com.ae2webmanagement.ae2webmanagementmod.command.CraftCommand;
import com.ae2webmanagement.ae2webmanagementmod.data.AccountStore;
import com.ae2webmanagement.ae2webmanagementmod.data.GridPasswords;
import com.ae2webmanagement.ae2webmanagementmod.grid.GridIdentity;
import com.ae2webmanagement.ae2webmanagementmod.icon.IconExporter;
import com.ae2webmanagement.ae2webmanagementmod.icon.IconPaths;
import com.ae2webmanagement.ae2webmanagementmod.snapshot.Snapshot;
import com.ae2webmanagement.ae2webmanagementmod.snapshot.SnapshotManager;

/**
 * 模组本地数据 API。
 *
 * <p>仅绑定回环地址。GET 只读取并发缓存；POST 只把合成意图交给
 * {@link CommandManager} 的线程安全队列；DELETE 同步等待主线程原子删除命令
 * 记录（响应即最终结果）；世界与 AE2 状态始终在主线程处理。</p>
 */
public final class LocalDataServer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_REQUEST_BYTES = 64 * 1024;

    private final SnapshotManager snapshots;
    private final CommandManager commands;
    private final AccountManager accounts;
    private HttpServer server;
    private ExecutorService executor;

    /** 注入线程安全的快照缓存、命令队列与账号镜像。 */
    public LocalDataServer(SnapshotManager snapshots, CommandManager commands, AccountManager accounts) {
        this.snapshots = snapshots;
        this.commands = commands;
        this.accounts = accounts;
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
            if ("DELETE".equalsIgnoreCase(method)) {
                handleDelete(exchange, path);
                return;
            }

            exchange.getResponseHeaders().set("Allow", "GET, POST, DELETE");
            sendError(exchange, 405, "method_not_allowed");
        } catch (Exception exception) {
            LOGGER.error("[AE2Web] Local data API request failed: {}", exchange.getRequestURI(), exception);
            sendError(exchange, 500, "internal_error");
        } finally {
            exchange.close();
        }
    }

    /** 分发只读接口：健康检查、账号列表、网格列表、单个网格快照和命令历史。 */
    private void handleGet(HttpExchange exchange, String path) throws IOException {
        if ("/health".equals(path)) {
            sendJson(exchange, 200, Map.of("ok", true));
            return;
        }
        if ("/accounts".equals(path)) {
            sendJson(exchange, 200, Map.of("accounts", listAccounts()));
            return;
        }
        if ("/grids".equals(path)) {
            sendJson(exchange, 200, Map.of("grids", listGrids()));
            return;
        }
        if (path.startsWith("/icons/")) {
            handleIcon(exchange, path.substring("/icons/".length()));
            return;
        }

        String[] parts = path.split("/");
        if (parts.length == 4 && "grids".equals(parts[1])) {
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
     * 处理图标静态文件请求（只读磁盘，绝不现算）。
     *
     * <p>图标由客户端 {@code IconService} 离屏渲染成 16×16 PNG 写入
     * {@code config/ae2webmanagement/icons/<ns>/<path>.png}；本端点只做
     * 路径校验与文件回读，HTTP 线程永不接触 Minecraft 状态（docs/roadmap.md §5）。</p>
     *
     * <p>路径穿越防护：namespace/path 只允许 {@code [a-z0-9_.-]}，拼接根目录后
     * {@code normalize} 并校验前缀，非法返回 400。缓存头与 JSON 端点的
     * {@code no-store} 不同：图标一经渲染即静态，给一天浏览器缓存。</p>
     *
     * <p>缺失返回 404 并做惰性补齐：在快照缓存中查找对应 key 入队，
     * 下一次请求大概率已渲染完成。</p>
     *
     * @param exchange    请求
     * @param relativePath {@code /icons/} 之后的路径，形如 {@code minecraft/iron_block.png}
     */
    private void handleIcon(HttpExchange exchange, String relativePath) throws IOException {
        String[] segments = relativePath.split("/");
        if (segments.length != 2
                || !isValidIconSegment(segments[0])
                || !isValidIconSegment(segments[1])) {
            sendError(exchange, 400, "invalid_icon_path");
            return;
        }
        String namespace = segments[0];
        String fileName = segments[1];
        if (!fileName.endsWith(".png")) {
            sendError(exchange, 400, "invalid_icon_path");
            return;
        }
        String path = fileName.substring(0, fileName.length() - 4);
        if (path.isEmpty() || !isValidIconSegment(path)) {
            sendError(exchange, 400, "invalid_icon_path");
            return;
        }

        Path base = IconPaths.root().normalize();
        Path target = base.resolve(namespace).resolve(fileName).normalize();
        if (!target.startsWith(base)) {
            sendError(exchange, 400, "invalid_icon_path");
            return;
        }

        if (!Files.isRegularFile(target)) {
            // 缺失 → 404 + 惰性补齐：把对应 key 的完整 NBT 交给客户端 IconService 入队
            this.snapshots.findKeyTagById(namespace + ":" + path)
                    .ifPresent(IconExporter.Global.get()::request);
            sendError(exchange, 404, "icon_not_found");
            return;
        }

        byte[] bytes = Files.readAllBytes(target);
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    /** 图标路径段只允许 {@code [a-z0-9_.-]}，杜绝路径穿越（配合 normalize + 前缀校验）。 */
    private static boolean isValidIconSegment(String segment) {
        return segment.matches("[a-z0-9_.-]+");
    }

    /**
     * 处理 POST 分发：合成命令投递与网格密码校验。
     */
    private void handlePost(HttpExchange exchange, String path) throws IOException {
        String[] parts = path.split("/");
        if (parts.length != 4 || !"grids".equals(parts[1])) {
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
     * 处理 DELETE 分发：删除合成命令记录（docs/contracts/index.md「合成命令 API」，模组 0.0.13）。
     *
     * <p>路径 {@code /grids/{accessId}/commands/{commandId}}；删除在主线程原子执行
     * （删除 PENDING 即撤销、删终态仅清理历史），响应即最终结果。记录不存在
     * （含已删除）→ 404 {@code command_not_found}，网格未知 → 404 {@code grid_not_found}，
     * 主线程不可用/超时 → 500 {@code internal_error}。</p>
     */
    private void handleDelete(HttpExchange exchange, String path) throws IOException {
        String[] parts = path.split("/");
        if (parts.length != 5 || !"grids".equals(parts[1]) || !"commands".equals(parts[3])) {
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
        UUID commandId;
        try {
            commandId = UUID.fromString(parts[4]);
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_command_id");
            return;
        }

        if (!this.commands.hasGrid(accessId)) {
            sendError(exchange, 404, "grid_not_found");
            return;
        }

        CommandManager.DeletionResult result = this.commands.delete(accessId, commandId);
        switch (result) {
            case DELETED -> sendEmpty(exchange, 204);
            case NOT_FOUND -> sendError(exchange, 404, "command_not_found");
            case UNAVAILABLE -> sendError(exchange, 500, "internal_error");
        }
    }

    /**
     * 处理合成命令投递。
     *
     * <p>只校验路径、网格、JSON 结构和基本数值；解析 AEKey 和访问 AE2
     * 交给主线程的 {@link CommandManager} 处理。
     */
    private void handleCraft(HttpExchange exchange, UUID accessId) throws IOException {
        if (!this.commands.hasGrid(accessId)) {
            sendError(exchange, 404, "grid_not_found");
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
            sendError(exchange, 404, "grid_not_found");
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
     * <p>未设密码的网格直接 204（公开）；密码哈希取自 SnapshotManager 的
     * 镜像缓存（GUI 设密后主线程立即刷新）。网格未知 → 404。</p>
     */
    private void handleVerifyPassword(HttpExchange exchange, UUID accessId) throws IOException {
        if (!this.snapshots.hasIdentity(accessId)) {
            sendError(exchange, 404, "grid_not_found");
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

    /** 组装网格列表：身份信息来自缓存，在线态和摘要来自最近快照。 */
    private List<Map<String, Object>> listGrids() {
        List<Map<String, Object>> grids = new ArrayList<>();
        for (GridIdentity identity : this.snapshots.getIdentities()) {
            Optional<Snapshot> snapshot = this.snapshots.get(identity.accessId());
            Map<String, Object> grid = new LinkedHashMap<>();
            grid.put("accessId", identity.accessId().toString());
            grid.put("displayName", identity.displayName());
            grid.put("dimension", identity.controllerLevel().location().toString());
            grid.put("controllerX", identity.controllerPos().getX());
            grid.put("controllerY", identity.controllerPos().getY());
            grid.put("controllerZ", identity.controllerPos().getZ());
            grid.put("online", snapshot.map(Snapshot::online).orElse(false));
            grid.put("capturedAt", snapshot.map(Snapshot::capturedAt).orElse(0L));
            grid.put("inventoryEntries", snapshot.map(value -> value.inventory().size()).orElse(0));
            grid.put("cpuCount", snapshot.map(value -> value.craftingCpus().size()).orElse(0));
            grid.put("hasPassword", !this.snapshots.getPasswordHash(identity.accessId()).isEmpty());
            // 最近一次在线时间（0.0.14 起随快照循环回写，契约 v0.10）
            grid.put("lastSeenAt", identity.lastSeenAt());
            // 注册者（0.1.0 起，契约「账号制」）：无主网格（旧存档）省略两个字段
            if (identity.ownerUuid() != null) {
                grid.put("ownerUuid", identity.ownerUuid().toString());
                String ownerName = this.accounts.nameOf(identity.ownerUuid());
                if (ownerName != null && !ownerName.isEmpty()) {
                    grid.put("ownerName", ownerName);
                }
            }
            grids.add(grid);
        }
        return grids;
    }

    /**
     * 组装账号列表（{@code GET /accounts}，0.1.0 起；供后端同步做本地登录校验）。
     *
     * <p>含 PBKDF2 哈希——后端在本地校验登录（明文不出模组），因此哈希必须在
     * 同步通道中传输；通道仅本机回环 + 共享密钥可达，泄漏面与网格密码一致。</p>
     */
    private List<Map<String, Object>> listAccounts() {
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (AccountStore.AccountEntry entry : this.accounts.getAll()) {
            Map<String, Object> account = new LinkedHashMap<>();
            account.put("playerUuid", entry.playerUuid().toString());
            account.put("playerName", entry.playerName());
            account.put("passwordHash", entry.passwordHash());
            account.put("createdAt", entry.createdAt());
            accounts.add(account);
        }
        return accounts;
    }

    /** 返回单个网格的库存与合成 CPU 快照。 */
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
            // CPU 无法命名：不传 name，前端按列表序号以「#N」展示（与游戏内一致）
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

    /**
     * 将一条库存/样板条目转换成 HTTP JSON。
     *
     * <p>{@code nameKey} 为可选字段（翻译键，前端查中文语言表，见 roadmap.md §4）：
     * 无翻译键时省略，向后兼容（旧后端/前端按 {@code {key, amount}} 处理）。</p>
     */
    private static Map<String, Object> stackToJson(Snapshot.StackEntry stack) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("key", toJson(stack.key()));
        json.put("amount", stack.amount());
        if (stack.nameKey() != null) {
            json.put("nameKey", stack.nameKey());
        }
        return json;
    }

    /** 将一组库存/样板条目转换成 HTTP JSON。 */
    private static List<Map<String, Object>> stacksToJson(List<Snapshot.StackEntry> stacks) {
        List<Map<String, Object>> result = new ArrayList<>(stacks.size());
        for (Snapshot.StackEntry stack : stacks) {
            result.add(stackToJson(stack));
        }
        return result;
    }

    /** 返回单个网格的全部命令历史和当前状态。 */
    private void sendCommands(HttpExchange exchange, UUID accessId) throws IOException {
        if (!this.commands.hasGrid(accessId)) {
            sendError(exchange, 404, "grid_not_found");
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
