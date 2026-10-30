package com.ae2webmanagement.ae2webmanagementmod.snapshot;

import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * 一次库存/合成状态快照，对应 docs/design.md §4.2。
 *
 * <p>库存使用 AE2 {@code getCachedInventory()} 的聚合值；{@code key} 保存
 * {@code AEKey#toTagGeneric} 生成的 NBT，避免直接持久化 AE2 内部对象。
 *
 * @param accessId     归属的网络身份
 * @param capturedAt   最近一次成功采样的 epoch 毫秒
 * @param online       采样时控制器区块是否加载且网格可用
 * @param inventory    物品种类及其数量
 * @param craftingCpus 合成 CPU 状态
 */
public record Snapshot(
        UUID accessId,
        long capturedAt,
        boolean online,
        List<StackEntry> inventory,
        List<CpuEntry> craftingCpus) {

    public Snapshot {
        inventory = List.copyOf(inventory);
        craftingCpus = List.copyOf(craftingCpus);
    }

    /** 一条库存记录。{@code key} 是 AEKey 的通用 NBT 表示。 */
    public record StackEntry(CompoundTag key, long amount) {
        public StackEntry {
            key = key.copy();
        }
    }

    /** 一个合成 CPU 的公开状态。 */
    public record CpuEntry(
            String name,
            boolean busy,
            long availableStorage,
            int coProcessors) {
    }
}
