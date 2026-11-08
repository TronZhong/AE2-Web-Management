package com.ae2webmanagement.backend.dto;

/**
 * 合成 CPU 条目：快照 {@code craftingCpus[]} 元素。
 *
 * <p>对应 docs/contracts/web-api.md「GET /api/grids/{accessId}/snapshot」的
 * {@code craftingCpus[]} 字段表。</p>
 *
 * @param name             CPU 名称；空字符串表示未命名
 * @param busy             是否正在执行合成任务
 * @param availableStorage 剩余可存储字节数
 * @param coProcessors     协处理器数量
 */
public record CpuEntry(String name, boolean busy, long availableStorage, int coProcessors) {
}
