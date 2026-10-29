package com.ae2webmanagement.ae2webmanagementmod.snapshot;

/**
 * 一次库存/合成状态快照，对应 docs/design.md §4.2。
 *
 * <p>库存使用 AE2 {@code getCachedInventory()} 的聚合值（物品种类 → 数量，不含节点来源信息）。
 * 字段里暂缺 inventory / craftingCpus —— 待接入 AE2 后确定 {@code AEKey} 的序列化方案，
 * 再加入（序列化契约见 docs/contracts）。
 *
 * @param capturedAt 采样时间（epoch 毫秒）
 * @param online     采样时网络是否在线（控制器区块已加载且 getGrid() 非 null）
 */
public record Snapshot(
        long capturedAt,
        boolean online) {
    // TODO 接入 AE2 后补充：Map<String, Long> inventory（AEKey 序列化 → 数量）、craftingCpus 列表。
}