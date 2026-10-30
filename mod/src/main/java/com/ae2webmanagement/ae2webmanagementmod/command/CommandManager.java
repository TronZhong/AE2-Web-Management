package com.ae2webmanagement.ae2webmanagementmod.command;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import appeng.blockentity.networking.ControllerBlockEntity;

import com.ae2webmanagement.ae2webmanagementmod.data.CommandStore;
import com.ae2webmanagement.ae2webmanagementmod.data.NetworkIdentityStore;
import com.ae2webmanagement.ae2webmanagementmod.network.NetworkIdentity;

/**
 * 合成命令的线程边界与主线程调度器。
 *
 * <p>HTTP 线程只调用 {@link #submit} 入队并立即返回；持久化、模拟和提交都在
 * 服务器主线程执行。模拟使用 AE2 的异步计算 Future，主线程只轮询完成状态，
 * 避免阻塞服务器 tick。
 */
public final class CommandManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long PLANNING_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(120);
    private static final int MAX_INCOMING_PER_TICK = 100;

    private final Map<UUID, CraftCommand> cache = new ConcurrentHashMap<>();
    private final Map<UUID, NetworkIdentity> identities = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<CraftCommand> incoming = new ConcurrentLinkedQueue<>();
    private final Map<UUID, PlanningJob> planning = new LinkedHashMap<>();

    /** 服务端启动时恢复命令缓存与身份索引。 */
    public void start(MinecraftServer server) {
        this.incoming.clear();
        this.planning.clear();
        this.cache.clear();
        this.identities.clear();
        NetworkIdentityStore.get(server.overworld()).getAll()
                .forEach(identity -> this.identities.put(identity.accessId(), identity));
        CommandStore.get(server.overworld()).getAll()
                .forEach(command -> this.cache.put(command.id(), command));
        LOGGER.info("[AE2Web] Command manager started with {} cached commands", this.cache.size());
    }

    /**
     * HTTP 线程入口：只做校验与入队，实际落盘发生在下一次主线程 tick。
     *
     * @return 已受理命令；网络不存在或参数非法时为空
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
        NetworkIdentityStore.get(server.overworld()).getAll()
                .forEach(identity -> this.identities.put(identity.accessId(), identity));

        persistIncoming(store);
        startPendingCalculations(server, store);
        completeCalculations(server, store);
    }

    public boolean hasNetwork(UUID accessId) {
        return this.identities.containsKey(accessId);
    }

    public List<CraftCommand> getCommands(UUID accessId) {
        return this.cache.values().stream()
                .filter(command -> command.accessId().equals(accessId))
                .sorted((left, right) -> Long.compare(left.submittedAt(), right.submittedAt()))
                .toList();
    }

    public void stop() {
        for (PlanningJob job : this.planning.values()) {
            job.future().cancel(false);
        }
        this.planning.clear();
        this.incoming.clear();
        this.cache.clear();
        this.identities.clear();
    }

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
            LOGGER.info("[AE2Web] Started crafting calculation {} for network {}",
                    command.id(), command.accessId());
        } catch (RuntimeException exception) {
            LOGGER.warn("[AE2Web] Could not start crafting calculation {}", command.id(), exception);
            fail(store, command, "calculation start failed: " + exception.getMessage());
        }
    }

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
                fail(store, command.get(), "calculation returned a simulation plan");
                iterator.remove();
                continue;
            }

            submitPlan(server, store, command.get(), plan);
            iterator.remove();
        }
    }

    private void submitPlan(MinecraftServer server, CommandStore store, CraftCommand command, ICraftingPlan plan) {
        Optional<CraftingContext> context = findContext(server, command.accessId());
        if (context.isEmpty()) {
            fail(store, command, "network became offline before submission");
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
                LOGGER.info("[AE2Web] Submitted crafting command {} for network {} (craftingId={})",
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

    private Optional<CraftingContext> findContext(MinecraftServer server, UUID accessId) {
        NetworkIdentity identity = this.identities.get(accessId);
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

    private void update(CommandStore store, CraftCommand command) {
        store.put(command);
        this.cache.put(command.id(), command);
    }

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

        @Override
        public IGridNode getActionableNode() {
            return this.node;
        }

        @Override
        public ImmutableSet<ICraftingLink> getRequestedJobs() {
            return ImmutableSet.of();
        }

        @Override
        public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
            // 顶层合成产物由 AE2 的 CPU 完成时直接存入网络；此请求者不接收节点投料。
            return 0;
        }

        @Override
        public void jobStateChange(ICraftingLink link) {
            // 当前契约只记录到 SUBMITTED；后续可在此回写 COMPLETED/CANCELLED。
        }
    }
}
