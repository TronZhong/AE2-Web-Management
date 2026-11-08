package com.ae2webmanagement.ae2webmanagementmod.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.networking.ControllerBlockEntity;

import com.ae2webmanagement.ae2webmanagementmod.Config;
import com.ae2webmanagement.ae2webmanagementmod.data.NetworkIdentityStore;
import com.ae2webmanagement.ae2webmanagementmod.data.SnapshotStore;
import com.ae2webmanagement.ae2webmanagementmod.network.NetworkIdentity;

/**
 * 在主线程周期采集网络快照，并维护供 HTTP 层读取的线程安全缓存。
 */
public final class SnapshotManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, Snapshot> cache = new ConcurrentHashMap<>();
    private final Map<UUID, NetworkIdentity> identityCache = new ConcurrentHashMap<>();
    /** 密码哈希镜像（主线程写 / HTTP 线程读）：GUI 设置密码后立即更新，不等到下一采样周期。 */
    private final Map<UUID, String> passwordHashCache = new ConcurrentHashMap<>();
    private long nextSnapshotAtMillis;

    /** 服务端启动时从 Overworld 的 SavedData 恢复最后快照。 */
    public void start(MinecraftServer server) {
        this.cache.clear();
        this.identityCache.clear();
        this.passwordHashCache.clear();
        ServerLevel overworld = server.overworld();
        NetworkIdentityStore.get(overworld).getAll()
                .forEach(identity -> {
                    this.identityCache.put(identity.accessId(), identity);
                    this.passwordHashCache.put(identity.accessId(), hashOf(identity));
                });
        SnapshotStore.get(overworld).getAll()
                .forEach(snapshot -> this.cache.put(snapshot.accessId(), snapshot));
        this.nextSnapshotAtMillis = 0L;
        LOGGER.info("[AE2Web] Snapshot manager started with {} cached snapshots", this.cache.size());
    }

    /** 在主线程执行采样；快照间隔来自配置，默认 4 秒。 */
    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now < this.nextSnapshotAtMillis) {
            return;
        }
        this.nextSnapshotAtMillis = now + Math.max(1, Config.SNAPSHOT_INTERVAL_SECONDS.get()) * 1000L;

        ServerLevel overworld = server.overworld();
        NetworkIdentityStore identityStore = NetworkIdentityStore.get(overworld);
        SnapshotStore snapshots = SnapshotStore.get(overworld);

        Map<UUID, NetworkIdentity> latestIdentities = new HashMap<>();
        for (NetworkIdentity identity : identityStore.getAll()) {
            latestIdentities.put(identity.accessId(), identity);
            Snapshot previous = this.cache.get(identity.accessId());
            Snapshot current = capture(server, identity, previous);
            this.cache.put(identity.accessId(), current);
            snapshots.put(current);

            if (previous == null) {
                LOGGER.info("[AE2Web] Captured startup snapshot for {}: online={}, inventory={}, cpus={}",
                        identity.accessId(), current.online(), current.inventory().size(), current.craftingCpus().size());
            }
        }
        this.identityCache.keySet().retainAll(latestIdentities.keySet());
        this.identityCache.putAll(latestIdentities);
        latestIdentities.forEach((accessId, identity) -> this.passwordHashCache.put(accessId, hashOf(identity)));
        this.passwordHashCache.keySet().retainAll(latestIdentities.keySet());
    }

    /** 服务端停止时清空内存缓存；持久化的最后快照保留在 SavedData 中。 */
    public void stop() {
        this.cache.clear();
        this.identityCache.clear();
        this.passwordHashCache.clear();
    }

    /** 按 accessId 获取最近一次快照；离线时可能是旧快照。 */
    public Optional<Snapshot> get(UUID accessId) {
        return Optional.ofNullable(this.cache.get(accessId));
    }

    /** 获取全部网络最近快照的只读副本。 */
    public Collection<Snapshot> getAll() {
        return List.copyOf(this.cache.values());
    }

    /** 获取全部网络身份的只读副本，供 HTTP 网络列表使用。 */
    public Collection<NetworkIdentity> getIdentities() {
        return List.copyOf(this.identityCache.values());
    }

    /**
     * @param accessId 网络接入 ID
     * @return 该网络的密码哈希；未设密码或网络未知为空字符串（HTTP 线程可调用）
     */
    public String getPasswordHash(UUID accessId) {
        return this.passwordHashCache.getOrDefault(accessId, "");
    }

    /**
     * GUI 设置/清除密码后由主线程立即同步，避免等待下一个采样周期。
     *
     * @param accessId 网络接入 ID
     * @param hash     新的密码哈希；清除密码传空字符串
     */
    public void updatePasswordHash(UUID accessId, String hash) {
        this.passwordHashCache.put(accessId, hash == null ? "" : hash);
    }

    /**
     * GUI 改名后由主线程立即同步身份镜像缓存（含密码哈希），
     * 让 HTTP 网络列表无需等待下一个采样周期即可展示新显示名。
     *
     * <p>对应 roadmap 已完成项（GUI 迭代）：接入方块 GUI 支持 displayName 改名。</p>
     *
     * @param identity 更新后的身份记录（displayName 已改）
     */
    public void updateIdentity(NetworkIdentity identity) {
        this.identityCache.put(identity.accessId(), identity);
        this.passwordHashCache.put(identity.accessId(), hashOf(identity));
    }

    /**
     * @param accessId 网络接入 ID
     * @return 身份表是否存在该网络（HTTP 线程可调用）
     */
    public boolean hasIdentity(UUID accessId) {
        return this.identityCache.containsKey(accessId);
    }

    /** 取身份的密码哈希（null 归一为空串）。 */
    private static String hashOf(NetworkIdentity identity) {
        return identity.passwordHash() == null ? "" : identity.passwordHash();
    }

    /**
     * 采集一个网络的当前状态。
     *
     * <p>先检查控制器所在区块和 AE2 网格是否在线；在线时读取网络库存缓存、
     * 合成 CPU 状态和样板摘要，离线时保留上一次快照但把 {@code online} 置为 false。
     */
    private Snapshot capture(MinecraftServer server, NetworkIdentity identity, Snapshot previous) {
        ServerLevel level = server.getLevel(identity.controllerLevel());
        if (level == null || !level.isLoaded(identity.controllerPos())) {
            return offline(identity.accessId(), previous);
        }

        BlockEntity blockEntity = level.getBlockEntity(identity.controllerPos());
        if (!(blockEntity instanceof ControllerBlockEntity controller)) {
            return offline(identity.accessId(), previous);
        }

        IGrid grid = controller.getMainNode().getGrid();
        if (grid == null) {
            return offline(identity.accessId(), previous);
        }

        IStorageService storage = grid.getService(IStorageService.class);
        ICraftingService crafting = grid.getService(ICraftingService.class);
        if (storage == null || crafting == null) {
            return offline(identity.accessId(), previous);
        }

        List<Snapshot.StackEntry> inventory = new ArrayList<>();
        for (var stack : storage.getCachedInventory()) {
            if (stack.getLongValue() <= 0) {
                continue;
            }
            inventory.add(new Snapshot.StackEntry(
                    stack.getKey().toTagGeneric(level.registryAccess()),
                    stack.getLongValue()));
        }

        List<Snapshot.CpuEntry> cpus = new ArrayList<>();
        for (ICraftingCPU cpu : crafting.getCpus()) {
            cpus.add(new Snapshot.CpuEntry(
                    cpuName(cpu),
                    cpu.isBusy(),
                    cpu.getAvailableStorage(),
                    cpu.getCoProcessors()));
        }

        return new Snapshot(
                identity.accessId(),
                System.currentTimeMillis(),
                true,
                inventory,
                cpus,
                capturePatterns(grid, level));
    }

    /**
     * 采集一个网络的样板摘要，对应 docs/design.md §9 的
     * {@code ICraftingProvider.getAvailablePatterns()} + {@code IPatternDetails}。
     *
     * <p>AE2 公开 API 没有“枚举全部样板”的入口，因此遍历网格节点逐一取
     * {@code ICraftingProvider}；同一编码样板放进多个样板供应器会重复出现，
     * 按 {@code IPatternDetails} 的 equals 契约用 {@link LinkedHashSet} 去重。
     * 结果按主输出排序，保证两次快照之间顺序稳定、便于前端对比。
     *
     * <p>成本：{@code getAvailablePatterns()} 是供应器内部已解码列表的 O(1) getter，
     * 全量重建（含 {@code AEKey#toTagGeneric}）每 4 秒一次，与库存采集同构。
     */
    private static List<Snapshot.PatternEntry> capturePatterns(IGrid grid, ServerLevel level) {
        Set<IPatternDetails> uniquePatterns = new LinkedHashSet<>();
        for (IGridNode node : grid.getNodes()) {
            ICraftingProvider provider = node.getService(ICraftingProvider.class);
            if (provider != null) {
                uniquePatterns.addAll(provider.getAvailablePatterns());
            }
        }

        List<Snapshot.PatternEntry> patterns = new ArrayList<>(uniquePatterns.size());
        for (IPatternDetails details : uniquePatterns) {
            if (details.getOutputs().isEmpty()) {
                continue; // 防御：无输出的样板无法在网页上表达，跳过
            }
            patterns.add(toPatternEntry(details, level));
        }
        patterns.sort(Comparator.comparing(
                (Snapshot.PatternEntry pattern) -> pattern.primaryOutput().key().toString()));
        return patterns;
    }

    /**
     * 把 AE2 的 {@link IPatternDetails} 映射为快照样板摘要，字段对应
     * docs/contracts/index.md 的 patterns 形状；输入槽实际需求量 =
     * {@code possibleInputs[i].amount × multiplier}。
     */
    private static Snapshot.PatternEntry toPatternEntry(IPatternDetails details, ServerLevel level) {
        List<Snapshot.StackEntry> outputs = new ArrayList<>();
        for (GenericStack output : details.getOutputs()) {
            outputs.add(toStackEntry(output, level));
        }

        List<Snapshot.PatternEntry.PatternInputEntry> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length == 0) {
                continue; // 防御：空输入槽不进入摘要
            }
            inputs.add(new Snapshot.PatternEntry.PatternInputEntry(
                    toStackEntries(possibleInputs, level),
                    input.getMultiplier()));
        }

        return new Snapshot.PatternEntry(
                toStackEntry(details.getPrimaryOutput(), level),
                outputs,
                inputs);
    }

    /** 把 AE2 {@link GenericStack} 转成快照条目：AEKey 存通用 NBT，数量为 long。 */
    private static Snapshot.StackEntry toStackEntry(GenericStack stack, ServerLevel level) {
        return new Snapshot.StackEntry(
                stack.what().toTagGeneric(level.registryAccess()),
                stack.amount());
    }

    /** 批量把 AE2 {@link GenericStack} 数组转成快照条目列表。 */
    private static List<Snapshot.StackEntry> toStackEntries(GenericStack[] stacks, ServerLevel level) {
        List<Snapshot.StackEntry> entries = new ArrayList<>(stacks.length);
        for (GenericStack stack : stacks) {
            entries.add(toStackEntry(stack, level));
        }
        return entries;
    }

    /**
     * AE2 的 {@link ICraftingCPU#getName()} 允许返回 {@code null}：
     * 未命名 CPU 的名称不会自动生成。快照契约统一用空字符串表示未命名。
     */
    private static String cpuName(ICraftingCPU cpu) {
        Component name = cpu.getName();
        return name == null ? "" : name.getString();
    }

    /**
     * 生成本次网络离线时的快照。
     *
     * <p>如果此前没有快照，则使用空数据和时间戳 0；否则保留旧数据，
     * 只刷新在线标记，方便网页显示“数据截至”。
     */
    private static Snapshot offline(UUID accessId, Snapshot previous) {
        if (previous == null) {
            return new Snapshot(accessId, 0L, false, List.of(), List.of(), List.of());
        }
        return new Snapshot(
                accessId,
                previous.capturedAt(),
                false,
                previous.inventory(),
                previous.craftingCpus(),
                previous.patterns());
    }
}
