package com.ae2webmanagement.backend.dto;

import java.util.List;

/**
 * 单网络完整快照：{@code GET /api/networks/{accessId}/snapshot} 的返回体。
 *
 * <p>对应 docs/contracts/web-api.md「GET /api/networks/{accessId}/snapshot」
 * （模组契约 GET /networks/{accessId}/snapshot 同构）；字段透传、不重命名。</p>
 *
 * @param accessId     归属网络（UUID）
 * @param capturedAt   采样时间（epoch 毫秒），页面显示「数据截至 xx」
 * @param online       采样时控制器区块是否加载
 * @param inventory    库存列表
 * @param craftingCpus 合成 CPU 列表
 * @param patterns     样板摘要列表
 */
public record SnapshotResponse(
		String accessId,
		long capturedAt,
		boolean online,
		List<StackEntry> inventory,
		List<CpuEntry> craftingCpus,
		List<PatternEntry> patterns) {
}
