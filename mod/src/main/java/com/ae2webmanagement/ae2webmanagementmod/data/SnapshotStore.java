package com.ae2webmanagement.ae2webmanagementmod.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import com.ae2webmanagement.ae2webmanagementmod.snapshot.Snapshot;

/**
 * 最后一次网络快照的持久化表，始终挂载在 Overworld。
 *
 * <p>运行时的实时读取应使用 {@code SnapshotManager} 的内存缓存；本表只负责
 * 重启后的离线展示，不供 HTTP 线程直接访问。
 */
public final class SnapshotStore extends SavedData {

    private static final String SAVED_DATA_KEY = "ae2web_snapshots";
    private static final String KEY_SNAPSHOTS = "snapshots";
    private static final String KEY_ACCESS_ID = "accessId";
    private static final String KEY_CAPTURED_AT = "capturedAt";
    private static final String KEY_ONLINE = "online";
    private static final String KEY_INVENTORY = "inventory";
    private static final String KEY_STACK = "key";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_CPUS = "craftingCpus";
    private static final String KEY_CPU_NAME = "name";
    private static final String KEY_CPU_BUSY = "busy";
    private static final String KEY_CPU_STORAGE = "availableStorage";
    private static final String KEY_CPU_COPROCESSORS = "coProcessors";

    private final Map<UUID, Snapshot> snapshots = new HashMap<>();

    public SnapshotStore() {
    }

    /** 获取存档级快照表；始终从 Overworld 读取。 */
    public static SnapshotStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SnapshotStore::new, SnapshotStore::load),
                SAVED_DATA_KEY);
    }

    public static SnapshotStore load(CompoundTag tag, HolderLookup.Provider registries) {
        SnapshotStore store = new SnapshotStore();
        ListTag list = tag.getList(KEY_SNAPSHOTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID accessId = entry.getUUID(KEY_ACCESS_ID);
            long capturedAt = entry.getLong(KEY_CAPTURED_AT);
            boolean online = entry.getBoolean(KEY_ONLINE);

            ListTag inventoryTag = entry.getList(KEY_INVENTORY, Tag.TAG_COMPOUND);
            List<Snapshot.StackEntry> inventory = new ArrayList<>(inventoryTag.size());
            for (int j = 0; j < inventoryTag.size(); j++) {
                CompoundTag stack = inventoryTag.getCompound(j);
                inventory.add(new Snapshot.StackEntry(
                        stack.getCompound(KEY_STACK).copy(),
                        stack.getLong(KEY_AMOUNT)));
            }

            ListTag cpuTag = entry.getList(KEY_CPUS, Tag.TAG_COMPOUND);
            List<Snapshot.CpuEntry> cpus = new ArrayList<>(cpuTag.size());
            for (int j = 0; j < cpuTag.size(); j++) {
                CompoundTag cpu = cpuTag.getCompound(j);
                cpus.add(new Snapshot.CpuEntry(
                        cpu.getString(KEY_CPU_NAME),
                        cpu.getBoolean(KEY_CPU_BUSY),
                        cpu.getLong(KEY_CPU_STORAGE),
                        cpu.getInt(KEY_CPU_COPROCESSORS)));
            }

            store.snapshots.put(accessId, new Snapshot(accessId, capturedAt, online, inventory, cpus));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Snapshot snapshot : snapshots.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_ACCESS_ID, snapshot.accessId());
            entry.putLong(KEY_CAPTURED_AT, snapshot.capturedAt());
            entry.putBoolean(KEY_ONLINE, snapshot.online());

            ListTag inventoryTag = new ListTag();
            for (Snapshot.StackEntry stack : snapshot.inventory()) {
                CompoundTag stackTag = new CompoundTag();
                stackTag.put(KEY_STACK, stack.key().copy());
                stackTag.putLong(KEY_AMOUNT, stack.amount());
                inventoryTag.add(stackTag);
            }
            entry.put(KEY_INVENTORY, inventoryTag);

            ListTag cpuTag = new ListTag();
            for (Snapshot.CpuEntry cpu : snapshot.craftingCpus()) {
                CompoundTag cpuEntry = new CompoundTag();
                cpuEntry.putString(KEY_CPU_NAME, cpu.name());
                cpuEntry.putBoolean(KEY_CPU_BUSY, cpu.busy());
                cpuEntry.putLong(KEY_CPU_STORAGE, cpu.availableStorage());
                cpuEntry.putInt(KEY_CPU_COPROCESSORS, cpu.coProcessors());
                cpuTag.add(cpuEntry);
            }
            entry.put(KEY_CPUS, cpuTag);

            list.add(entry);
        }
        tag.put(KEY_SNAPSHOTS, list);
        return tag;
    }

    public void put(Snapshot snapshot) {
        this.snapshots.put(snapshot.accessId(), snapshot);
        setDirty();
    }

    public Optional<Snapshot> get(UUID accessId) {
        return Optional.ofNullable(this.snapshots.get(accessId));
    }

    public Collection<Snapshot> getAll() {
        return Collections.unmodifiableCollection(this.snapshots.values());
    }
}
