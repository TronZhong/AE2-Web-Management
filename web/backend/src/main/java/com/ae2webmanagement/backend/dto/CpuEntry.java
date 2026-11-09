package com.ae2webmanagement.backend.dto;

/**
 * 合成 CPU 条目：快照 {@code craftingCpus[]} 元素。
 *
 * <p>对应 docs/contracts/web-api.md「GET /api/grids/{accessId}/snapshot」的
 * {@code craftingCpus[]} 字段表。AE2 合成 CPU 无法命名（契约 v0.8 / 模组 0.0.12 起
 * 不再传输 {@code name}），网页端与游戏内一致按列表序号以「#N」展示。</p>
 *
 * @param busy             是否正在执行合成任务
 * @param availableStorage 剩余可存储字节数
 * @param coProcessors     协处理器数量
 */
public record CpuEntry(boolean busy, long availableStorage, int coProcessors) {
}
