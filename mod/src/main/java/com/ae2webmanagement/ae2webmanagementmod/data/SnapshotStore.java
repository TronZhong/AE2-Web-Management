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
    private static final String KEY_PATTERNS = "patterns";
    private static final String KEY_PRIMARY_OUTPUT = "primaryOutput";
    private static final String KEY_OUTPUTS = "outputs";
    private static final String KEY_INPUTS = "inputs";
    private static final String KEY_POSSIBLE_INPUTS = "possibleInputs";
    private static final String KEY_MULTIPLIER = "multiplier";
    private static final String KEY_CPU_NAME = "name";
    private static final String KEY_CPU_BUSY = "busy";
    private static final String KEY_CPU_STORAGE = "availableStorage";
    private static final String KEY_CPU_COPROCESSORS = "coProcessors";

    private final Map<UUID, Snapshot> snapshots = new HashMap<>();

    /** 创建空表；通常由 Minecraft 的 SavedData 工厂调用。 */
    public SnapshotStore() {
    }

    /** 获取存档级快照表；始终从 Overworld 读取。 */
    public static SnapshotStore get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SnapshotStore::new, SnapshotStore::load),
                SAVED_DATA_KEY);
    }

    /** 从 SavedData NBT 恢复每个网络的最后一次快照。 */
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

            List<Snapshot.PatternEntry> patterns = loadPatterns(entry);

            store.snapshots.put(accessId,
                    new Snapshot(accessId, capturedAt, online, inventory, cpus, patterns));
        }
        return store;
    }

    /** 把全部快照序列化为 SavedData NBT。 */
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
            entry.put(KEY_PATTERNS, savePatterns(snapshot.patterns()));

            list.add(entry);
        }
        tag.put(KEY_SNAPSHOTS, list);
        return tag;
    }

    /** 从 NBT 恢复样板摘要；旧存档没有该字段时得到空列表。 */
    private static List<Snapshot.PatternEntry> loadPatterns(CompoundTag entry) {
        ListTag patternTag = entry.getList(KEY_PATTERNS, Tag.TAG_COMPOUND);
        List<Snapshot.PatternEntry> patterns = new ArrayList<>(patternTag.size());
        for (int i = 0; i < patternTag.size(); i++) {
            CompoundTag pattern = patternTag.getCompound(i);
            patterns.add(new Snapshot.PatternEntry(
                    loadStack(pattern.getCompound(KEY_PRIMARY_OUTPUT)),
                    loadStacks(pattern.getList(KEY_OUTPUTS, Tag.TAG_COMPOUND)),
                    loadPatternInputs(pattern.getList(KEY_INPUTS, Tag.TAG_COMPOUND))));
        }
        return patterns;
    }

    /** 把样板摘要序列化为 SavedData NBT。 */
    private static ListTag savePatterns(List<Snapshot.PatternEntry> patterns) {
        ListTag patternTag = new ListTag();
        for (Snapshot.PatternEntry pattern : patterns) {
            CompoundTag entry = new CompoundTag();
            entry.put(KEY_PRIMARY_OUTPUT, saveStack(pattern.primaryOutput()));
            entry.put(KEY_OUTPUTS, saveStacks(pattern.outputs()));
            entry.put(KEY_INPUTS, savePatternInputs(pattern.inputs()));
            patternTag.add(entry);
        }
        return patternTag;
    }

    /** 从 NBT 恢复样板输入槽；每个输入槽包含候选物品列表和倍率。 */
    private static List<Snapshot.PatternEntry.PatternInputEntry> loadPatternInputs(ListTag tag) {
        List<Snapshot.PatternEntry.PatternInputEntry> inputs = new ArrayList<>(tag.size());
        for (int i = 0; i < tag.size(); i++) {
            CompoundTag input = tag.getCompound(i);
            inputs.add(new Snapshot.PatternEntry.PatternInputEntry(
                    loadStacks(input.getList(KEY_POSSIBLE_INPUTS, Tag.TAG_COMPOUND)),
                    input.getLong(KEY_MULTIPLIER)));
        }
        return inputs;
    }

    /** 把样板输入槽序列化为 SavedData NBT。 */
    private static ListTag savePatternInputs(List<Snapshot.PatternEntry.PatternInputEntry> inputs) {
        ListTag tag = new ListTag();
        for (Snapshot.PatternEntry.PatternInputEntry input : inputs) {
            CompoundTag entry = new CompoundTag();
            entry.put(KEY_POSSIBLE_INPUTS, saveStacks(input.possibleInputs()));
            entry.putLong(KEY_MULTIPLIER, input.multiplier());
            tag.add(entry);
        }
        return tag;
    }

    /** 从 NBT 恢复一条 AEKey + amount 记录。 */
    private static Snapshot.StackEntry loadStack(CompoundTag tag) {
        return new Snapshot.StackEntry(tag.getCompound(KEY_STACK).copy(), tag.getLong(KEY_AMOUNT));
    }

    /** 从 NBT 恢复一组 AEKey + amount 记录。 */
    private static List<Snapshot.StackEntry> loadStacks(ListTag tag) {
        List<Snapshot.StackEntry> stacks = new ArrayList<>(tag.size());
        for (int i = 0; i < tag.size(); i++) {
            stacks.add(loadStack(tag.getCompound(i)));
        }
        return stacks;
    }

    /** 把一条 AEKey + amount 记录序列化为 SavedData NBT。 */
    private static CompoundTag saveStack(Snapshot.StackEntry stack) {
        CompoundTag entry = new CompoundTag();
        entry.put(KEY_STACK, stack.key().copy());
        entry.putLong(KEY_AMOUNT, stack.amount());
        return entry;
    }

    /** 把一组 AEKey + amount 记录序列化为 SavedData NBT。 */
    private static ListTag saveStacks(List<Snapshot.StackEntry> stacks) {
        ListTag tag = new ListTag();
        for (Snapshot.StackEntry stack : stacks) {
            tag.add(saveStack(stack));
        }
        return tag;
    }

    /** 新增或替换某网络的最后快照，并标记 SavedData 为脏数据。 */
    public void put(Snapshot snapshot) {
        this.snapshots.put(snapshot.accessId(), snapshot);
        setDirty();
    }

    /** 按网络身份读取最后快照。 */
    public Optional<Snapshot> get(UUID accessId) {
        return Optional.ofNullable(this.snapshots.get(accessId));
    }

    /** 获取全部最后快照的只读视图。 */
    public Collection<Snapshot> getAll() {
        return Collections.unmodifiableCollection(this.snapshots.values());
    }
}
