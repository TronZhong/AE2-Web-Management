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

    public void stop() {
        this.cache.clear();
        this.identityCache.clear();
    }

    public Optional<Snapshot> get(UUID accessId) {
        return Optional.ofNullable(this.cache.get(accessId));
    }

    public Collection<Snapshot> getAll() {
        return List.copyOf(this.cache.values());
    }

    public Collection<NetworkIdentity> getIdentities() {
        return List.copyOf(this.identityCache.values());
    }

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
                    cpu.getName().getString(),
                    cpu.isBusy(),
                    cpu.getAvailableStorage(),
                    cpu.getCoProcessors()));
        }

        return new Snapshot(identity.accessId(), System.currentTimeMillis(), true, inventory, cpus);
    }

    private static Snapshot offline(UUID accessId, Snapshot previous) {
        if (previous == null) {
            return new Snapshot(accessId, 0L, false, List.of(), List.of());
        }
        return new Snapshot(
                accessId,
                previous.capturedAt(),
                false,
                previous.inventory(),
                previous.craftingCpus());
    }
}
