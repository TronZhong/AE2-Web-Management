package com.ae2webmanagement.ae2webmanagementmod.command;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;

import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.networking.ControllerBlockEntity;

import com.ae2webmanagement.ae2webmanagementmod.Config;
import com.ae2webmanagement.ae2webmanagementmod.data.CommandStore;
import com.ae2webmanagement.ae2webmanagementmod.data.GridIdentityStore;
import com.ae2webmanagement.ae2webmanagementmod.grid.GridIdentity;

/**
 * 合成命令的线程边界与主线程调度器。
 *
 * <p>HTTP 线程只调用 {@link #submit} 入队并立即返回；持久化、模拟和提交都在
 * 服务器主线程执行。模拟使用 AE2 的异步计算 Future，主线程只轮询完成状态，
 * 避免阻塞服务器 tick。</p>
 *
 * <p>删除（{@link #delete}）与提交不同：为杜绝「已删命令仍被提交」的竞态，
 * HTTP 线程经 {@code MinecraftServer#execute} 把删除任务派发到主线程并同步等待
 * 结果（超时上限 {@value #DELETE_TIMEOUT_MILLIS}ms，小于后端读超时 5s），
 * 整个「查状态 → 取消在途计算 → 移除记录」在主线程一次跑完，响应即最终结果。</p>
 */
public final class CommandManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long PLANNING_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(120);
    private static final int MAX_INCOMING_PER_TICK = 100;
    /** 删除命令同步等待主线程的上限；须小于后端对模组的 5s 读超时。 */
    private static final long DELETE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(2);

    private final Map<UUID, CraftCommand> cache = new ConcurrentHashMap<>();
    private final Map<UUID, GridIdentity> identities = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<CraftCommand> incoming = new ConcurrentLinkedQueue<>();
    private final Map<UUID, PlanningJob> planning = new LinkedHashMap<>();
    /** 服务端实例（start 时持有，stop 时置空）：供 HTTP 线程把删除任务派发到主线程。 */
    private volatile MinecraftServer server;

    /** 服务端启动时恢复命令缓存与身份索引。 */
    public void start(MinecraftServer server) {
        this.incoming.clear();
        this.planning.clear();
        this.cache.clear();
        this.identities.clear();
        this.server = server;
        GridIdentityStore.get(server.overworld()).getAll()
                .forEach(identity -> this.identities.put(identity.accessId(), identity));
        CommandStore store = CommandStore.get(server.overworld());
        store.getAll()
                .forEach(command -> this.cache.put(command.id(), command));
        // 存档可能残留超出上限的历史（旧版本遗留或配置调小）：启动时按网格清理一次
        store.getAll().stream()
                .map(CraftCommand::accessId)
                .distinct()
                .forEach(accessId -> pruneHistory(store, accessId));
        LOGGER.info("[AE2Web] Command manager started with {} cached commands", this.cache.size());
    }

    /**
     * HTTP 线程入口：只做校验与入队，实际落盘发生在下一次主线程 tick。
     *
     * @return 已受理命令；网格不存在或参数非法时为空
     */
    public Optional<CraftCommand> submit(UUID accessId, CompoundTag target, long amount) {
        if (!this.identities.containsKey(accessId) || target == null || target.isEmpty() || amount <= 0) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        CraftCommand command = new CraftCommand(
                UUID.randomUUID(), accessId, target, amount,
                CommandState.PENDING, now, "queued");
        this.incoming.add(command);
        this.cache.put(command.id(), command);
        return Optional.of(command);
    }

    /** 主线程 tick：落盘新命令、启动重算、收割异步计算结果。 */
    public void tick(MinecraftServer server) {
        CommandStore store = CommandStore.get(server.overworld());

        // Overworld 身份表可能由 GUI 注册产生新记录；主线程 tick 时刷新 HTTP 层可用的索引。
        GridIdentityStore.get(server.overworld()).getAll()
                .forEach(identity -> this.identities.put(identity.accessId(), identity));

        persistIncoming(store);
        startPendingCalculations(server, store);
        completeCalculations(server, store);
    }

    public boolean hasGrid(UUID accessId) {
        return this.identities.containsKey(accessId);
    }

    /** 按提交时间升序返回某个网格的所有命令；数据来自运行时缓存，供 HTTP 查询使用。 */
    public List<CraftCommand> getCommands(UUID accessId) {
        return this.cache.values().stream()
                .filter(command -> command.accessId().equals(accessId))
                .sorted((left, right) -> Long.compare(left.submittedAt(), right.submittedAt()))
                .toList();
    }

    /**
     * 服务端停止时清空运行时状态。
     *
     * <p>未完成的 AE2 异步计算会被取消；已持久化到 {@link CommandStore} 的
     * 命令历史保留在存档中，下次启动时恢复。服务端引用同时置空，
     * 让后续 HTTP 删除请求得到「不可用」而非永久挂起。</p>
     */
    public void stop() {
        for (PlanningJob job : this.planning.values()) {
            job.future().cancel(false);
        }
        this.planning.clear();
        this.incoming.clear();
        this.cache.clear();
        this.identities.clear();
        this.server = null;
    }

    /**
     * 把 HTTP 线程入队的新命令写入持久化表。
     *
     * <p>每 tick 最多处理 {@value #MAX_INCOMING_PER_TICK} 条，防止突发请求
     * 在一个 tick 内产生过多磁盘写入。
     */
    private void persistIncoming(CommandStore store) {
        for (int i = 0; i < MAX_INCOMING_PER_TICK; i++) {
            CraftCommand command = this.incoming.poll();
            if (command == null) {
                return;
            }
            store.put(command);
            this.cache.put(command.id(), command);
        }
    }

    /**
     * 扫描所有待处理命令，并为在线网格的命令启动 AE2 重算。
     *
     * <p>离线网格的命令保持 {@code PENDING}，等网格恢复后自动继续。
     */
    private void startPendingCalculations(MinecraftServer server, CommandStore store) {
        for (CraftCommand command : List.copyOf(store.getAll())) {
            if (command.state() != CommandState.PENDING || this.planning.containsKey(command.id())) {
                continue;
            }

            Optional<CraftingContext> context = findContext(server, command.accessId());
            if (context.isEmpty()) {
                continue;
            }
            beginCalculation(store, command, context.get());
        }
    }

    /**
     * 解析命令目标并启动 AE2 异步重算。
     *
     * <p>这里只发起计算，不等待结果；成功返回的 Future 交给
     * {@link #completeCalculations} 在后续主线程 tick 中收割。
     */
    private void beginCalculation(CommandStore store, CraftCommand command, CraftingContext ctx) {
        Optional<AEKey> target;
        try {
            target = Optional.ofNullable(AEKey.fromTagGeneric(
                    ctx.level().registryAccess(), command.target()));
        } catch (RuntimeException exception) {
            target = Optional.empty();
        }
        if (target.isEmpty()) {
            fail(store, command, "invalid AEKey in command target");
            return;
        }

        WebCraftingRequester requester = new WebCraftingRequester(ctx.node());
        IActionSource actionSource = IActionSource.ofMachine(requester);
        try {
            Future<ICraftingPlan> future = ctx.crafting().beginCraftingCalculation(
                    ctx.level(), () -> actionSource, target.get(), command.amount(),
                    CalculationStrategy.REPORT_MISSING_ITEMS);
            this.planning.put(command.id(), new PlanningJob(future, System.currentTimeMillis()));
            update(store, command.withState(CommandState.PENDING, System.currentTimeMillis(), "simulating"));
            LOGGER.info("[AE2Web] Started crafting calculation {} for grid {}",
                    command.id(), command.accessId());
        } catch (RuntimeException exception) {
            LOGGER.warn("[AE2Web] Could not start crafting calculation {}", command.id(), exception);
            fail(store, command, "calculation start failed: " + exception.getMessage());
        }
    }

    /**
     * 收割已经完成的 AE2 重算结果。
     *
     * <p>可执行计划会交给 {@link #submitPlan} 提交；simulation plan、异常、
     * 超时或空结果都会把命令标记为 {@code FAILED}。
     */
    private void completeCalculations(MinecraftServer server, CommandStore store) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PlanningJob>> iterator = this.planning.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlanningJob> entry = iterator.next();
            PlanningJob job = entry.getValue();

            if (!job.future().isDone()) {
                if (now - job.startedAt() > PLANNING_TIMEOUT_MILLIS) {
                    job.future().cancel(false);
                    Optional<CraftCommand> command = store.get(entry.getKey());
                    command.ifPresent(value -> fail(store, value, "crafting calculation timed out"));
                    iterator.remove();
                }
                continue;
            }

            Optional<CraftCommand> command = store.get(entry.getKey());
            if (command.isEmpty()) {
                iterator.remove();
                continue;
            }

            ICraftingPlan plan;
            try {
                plan = job.future().get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fail(store, command.get(), "calculation interrupted");
                iterator.remove();
                continue;
            } catch (ExecutionException exception) {
                LOGGER.warn("[AE2Web] Crafting calculation failed for {}", entry.getKey(), exception);
                fail(store, command.get(), "calculation failed: " + exception.getCause());
                iterator.remove();
                continue;
            }

            if (plan == null) {
                fail(store, command.get(), "calculation returned no plan");
                iterator.remove();
                continue;
            }
            if (plan.simulation()) {
                fail(store, command.get(),
                        "AE2 can only create a simulation plan; missing: " + describeMissingItems(plan));
                iterator.remove();
                continue;
            }

            submitPlan(server, store, command.get(), plan);
            iterator.remove();
        }
    }

    /** 把 AE2 计划中的缺失物品转换成便于写入日志和 HTTP result 的文本。 */
    private static String describeMissingItems(ICraftingPlan plan) {
        KeyCounter missing = plan.missingItems();
        if (missing == null || missing.isEmpty()) {
            return "AE2 did not report missing items (check patterns/providers/CPU selection)";
        }

        StringBuilder text = new StringBuilder();
        for (var entry : missing) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(entry.getKey().getId()).append(" x").append(entry.getLongValue());
        }
        return text.toString();
    }

    /**
     * 把 AE2 已生成的可执行计划提交给目标网格。
     *
     * <p>提交成功时记录 AE2 的 craftingId；拒绝或异常时把命令标记为失败。
     */
    private void submitPlan(MinecraftServer server, CommandStore store, CraftCommand command, ICraftingPlan plan) {
        Optional<CraftingContext> context = findContext(server, command.accessId());
        if (context.isEmpty()) {
            fail(store, command, "grid became offline before submission");
            return;
        }

        CraftingContext ctx = context.get();
        WebCraftingRequester requester = new WebCraftingRequester(ctx.node());
        IActionSource actionSource = IActionSource.ofMachine(requester);
        try {
            ICraftingSubmitResult result = ctx.crafting().submitJob(plan, requester, null, false, actionSource);
            if (result.successful()) {
                String craftingId = result.link() == null ? "unknown" : result.link().getCraftingID().toString();
                update(store, command.withState(
                        CommandState.SUBMITTED, System.currentTimeMillis(),
                        "submitted to AE2; craftingId=" + craftingId));
                LOGGER.info("[AE2Web] Submitted crafting command {} for grid {} (craftingId={})",
                        command.id(), command.accessId(), craftingId);
            } else {
                String detail = result.errorDetail() == null ? "" : ": " + result.errorDetail();
                fail(store, command, "AE2 rejected job: " + result.errorCode() + detail);
                LOGGER.warn("[AE2Web] AE2 rejected crafting command {}: {}{}",
                        command.id(), result.errorCode(), detail);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("[AE2Web] AE2 submitJob failed for {}", command.id(), exception);
            fail(store, command, "submit failed: " + exception.getMessage());
        }
    }

    /**
     * 根据网格身份定位当前可用的 AE2 合成服务。
     *
     * <p>要求控制器所在区块已加载、网格在线、节点可用，且 crafting service 存在。
     */
    private Optional<CraftingContext> findContext(MinecraftServer server, UUID accessId) {
        GridIdentity identity = this.identities.get(accessId);
        if (identity == null) {
            return Optional.empty();
        }

        ServerLevel level = server.getLevel(identity.controllerLevel());
        if (level == null || !level.isLoaded(identity.controllerPos())) {
            return Optional.empty();
        }

        BlockEntity blockEntity = level.getBlockEntity(identity.controllerPos());
        if (!(blockEntity instanceof ControllerBlockEntity controller)) {
            return Optional.empty();
        }

        IGridNode node = controller.getMainNode().getNode();
        IGrid grid = node == null ? null : node.getGrid();
        if (grid == null || !node.isActive()) {
            return Optional.empty();
        }

        ICraftingService crafting = grid.getService(ICraftingService.class);
        if (crafting == null) {
            return Optional.empty();
        }
        return Optional.of(new CraftingContext(level, node, crafting));
    }

    /** 同步写入持久化表和 HTTP 可读的运行时缓存。 */
    private void update(CommandStore store, CraftCommand command) {
        store.put(command);
        this.cache.put(command.id(), command);
        // 终态写入后按网格修剪历史（PENDING 的中间态写入不触发）
        if (command.state() != CommandState.PENDING) {
            pruneHistory(store, command.accessId());
        }
    }

    /**
     * 按网格修剪超出上限的终态命令历史（主线程调用）。
     *
     * <p>每个网格只保留最近 {@code Config#COMMAND_HISTORY_LIMIT} 条
     * {@code SUBMITTED}/{@code FAILED} 记录，超出时按 {@code submittedAt}
     * 从旧到新从持久化表与运行时缓存同时移除；{@code PENDING} 记录
     * 永不自动清理（还在排队的命令不能悄悄丢）。</p>
     */
    private void pruneHistory(CommandStore store, UUID accessId) {
        int limit = Math.max(1, Config.COMMAND_HISTORY_LIMIT.get());
        List<CraftCommand> terminal = store.getForGrid(accessId).stream()
                .filter(command -> command.state() != CommandState.PENDING)
                .toList();
        // getForGrid 按提交时间升序排列，列表头部即最旧记录
        for (int i = 0; i < terminal.size() - limit; i++) {
            CraftCommand stale = terminal.get(i);
            store.remove(stale.id());
            this.cache.remove(stale.id());
        }
    }

    /** 删除命令请求的处理结果，供 HTTP 层映射响应码。 */
    public enum DeletionResult {
        /** 记录已删除。 */
        DELETED,
        /** 记录不存在（含已被删除）。 */
        NOT_FOUND,
        /** 主线程不可用或等待超时，结果未知（HTTP 层按 internal_error 处理）。 */
        UNAVAILABLE
    }

    /**
     * HTTP 线程入口：请求删除一条命令记录，同步等待主线程原子执行。
     *
     * <p>删除与主线程的模拟/提交串行化，响应即最终结果：删除 {@code PENDING}
     * 记录即撤销（不会提交给 AE2，含取消在途计算），删除终态记录仅清理历史。
     * 派发经 {@code MinecraftServer#execute}，等待上限 {@value #DELETE_TIMEOUT_MILLIS}ms。</p>
     *
     * @param accessId  目标网格
     * @param commandId 命令 ID
     * @return 删除结果；主线程关闭中或超时为 {@link DeletionResult#UNAVAILABLE}
     */
    public DeletionResult delete(UUID accessId, UUID commandId) {
        MinecraftServer current = this.server;
        if (current == null) {
            return DeletionResult.UNAVAILABLE;
        }

        CompletableFuture<Boolean> done = new CompletableFuture<>();
        try {
            current.execute(() -> {
                try {
                    done.complete(deleteOnMainThread(current, accessId, commandId));
                } catch (RuntimeException exception) {
                    done.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException exception) {
            LOGGER.warn("[AE2Web] Could not schedule command deletion {}", commandId, exception);
            return DeletionResult.UNAVAILABLE;
        }

        try {
            return done.get(DELETE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    ? DeletionResult.DELETED
                    : DeletionResult.NOT_FOUND;
        } catch (TimeoutException exception) {
            LOGGER.warn("[AE2Web] Timed out waiting for main thread to delete command {}", commandId);
            return DeletionResult.UNAVAILABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return DeletionResult.UNAVAILABLE;
        } catch (ExecutionException exception) {
            LOGGER.error("[AE2Web] Command deletion failed on main thread {}", commandId, exception);
            return DeletionResult.UNAVAILABLE;
        }
    }

    /**
     * 主线程原子执行删除：查状态 → 取消在途计算 → 移除持久化与缓存。
     *
     * <p>与 {@link #startPendingCalculations} / {@link #completeCalculations}
     * 在同一主线程串行化，因此不存在「删除后仍被提交」或「已删命令复活」的
     * 竞态：planning 条目与 store 条目同 tick 内一起消失。</p>
     *
     * @return 记录存在且已删除为 true；记录不存在（含已删除）为 false
     */
    private boolean deleteOnMainThread(MinecraftServer current, UUID accessId, UUID commandId) {
        CommandStore store = CommandStore.get(current.overworld());
        CraftCommand command = store.get(commandId).orElse(null);
        if (command == null || !command.accessId().equals(accessId)) {
            return false;
        }

        PlanningJob job = this.planning.remove(commandId);
        if (job != null) {
            // 不打断计算线程：结果已无人收割，仅阻止其继续占用
            job.future().cancel(false);
        }
        store.remove(commandId);
        this.cache.remove(commandId);
        LOGGER.info("[AE2Web] Deleted crafting command {} for grid {} (state was {})",
                commandId, accessId, command.state());
        return true;
    }

    /**
     * 删除网格时级联清理该网格全部命令记录（含 PENDING），主线程调用（roadmap §11）。
     *
     * <p>「删除网格」会连网页命令历史一起清除：取消在途 AE2 计算、移除运行时
     * 缓存与持久化记录、丢弃尚未落盘的入队命令，并同步移除身份索引
     * （否则 {@code hasGrid} 仍会对已删网格放行后续投递）。此后该网格的
     * craft/commands 请求得到 {@code grid_not_found}。</p>
     *
     * @param accessId 待清理的网格接入 ID
     * @param server   服务端实例（主线程事件上下文）
     */
    public void removeGridCommands(UUID accessId, MinecraftServer server) {
        CommandStore store = CommandStore.get(server.overworld());
        for (CraftCommand command : List.copyOf(store.getForGrid(accessId))) {
            PlanningJob job = this.planning.remove(command.id());
            if (job != null) {
                job.future().cancel(false);
            }
            store.remove(command.id());
            this.cache.remove(command.id());
        }
        // 丢弃 HTTP 线程刚入队、尚未被主线程消费的命令，避免删除后又被落盘
        this.incoming.removeIf(command -> command.accessId().equals(accessId));
        this.identities.remove(accessId);
    }

    /** 将命令标记为失败，并把原因保存到 result 字段。 */
    private void fail(CommandStore store, CraftCommand command, String result) {
        update(store, command.withState(CommandState.FAILED, System.currentTimeMillis(), result));
    }

    private record CraftingContext(ServerLevel level, IGridNode node, ICraftingService crafting) {
    }

    private record PlanningJob(Future<ICraftingPlan> future, long startedAt) {
    }

    /** 将网页命令包装成 AE2 可回传状态的请求者。 */
    private static final class WebCraftingRequester implements ICraftingRequester {

        private final IGridNode node;

        private WebCraftingRequester(IGridNode node) {
            this.node = node;
        }

        /** 返回 AE2 用来做权限、安全和网格归属判断的节点。 */
        @Override
        public IGridNode getActionableNode() {
            return this.node;
        }

        /** 当前请求者不保存待恢复链接；链接状态由 AE2 CraftingService 管理。 */
        @Override
        public ImmutableSet<ICraftingLink> getRequestedJobs() {
            return ImmutableSet.of();
        }

        /** 顶层合成产物由 AE2 的 CPU 完成时直接存入网格；此请求者不接收节点投料。 */
        @Override
        public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
            return 0;
        }

        /** 当前契约只记录到 SUBMITTED；后续可在此回写 COMPLETED/CANCELLED。 */
        @Override
        public void jobStateChange(ICraftingLink link) {
            // 预留：后续同步 AE2 链接完成/取消状态。
        }
    }
}
