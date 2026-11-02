package com.ae2webmanagement.ae2webmanagementmod.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.storage.IStorageService;
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
    private long nextSnapshotAtMillis;

    /** 服务端启动时从 Overworld 的 SavedData 恢复最后快照。 */
    public void start(MinecraftServer server) {
        this.cache.clear();
        this.identityCache.clear();
        ServerLevel overworld = server.overworld();
        NetworkIdentityStore.get(overworld).getAll()
                .forEach(identity -> this.identityCache.put(identity.accessId(), identity));
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
    }

    /** 服务端停止时清空内存缓存；持久化的最后快照保留在 SavedData 中。 */
    public void stop() {
        this.cache.clear();
        this.identityCache.clear();
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
     * 采集一个网络的当前状态。
     *
     * <p>先检查控制器所在区块和 AE2 网格是否在线；在线时读取网络库存缓存
     * 和合成 CPU 状态，离线时保留上一次快照但把 {@code online} 置为 false。
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

        return new Snapshot(identity.accessId(), System.currentTimeMillis(), true, inventory, cpus, List.of());
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
