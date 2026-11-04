package com.ae2webmanagement.backend.dto;

/**
 * 网络摘要：网络列表中单个网络的条目。
 *
 * <p>对应 docs/contracts/web-api.md「GET /api/networks」表的 {@code networks[]}
 * 元素（模组契约 GET /networks 同构）；字段透传、不重命名。</p>
 *
 * @param accessId         网络接入 ID（UUID），后续端点的路径参数
 * @param displayName      显示名（游戏内注册时命名）
 * @param dimension        控制器所在维度，如 {@code minecraft:overworld}
 * @param controllerX      ME 控制器 X 坐标
 * @param controllerY      ME 控制器 Y 坐标
 * @param controllerZ      ME 控制器 Z 坐标
 * @param online           最近一次快照的在线态
 * @param capturedAt       最近一次快照采样时间（epoch 毫秒）；从未采样为 0
 * @param inventoryEntries 库存物品种类数（非数量）
 * @param cpuCount         合成 CPU 数量
 */
public record NetworkSummaryResponse(
		String accessId,
		String displayName,
		String dimension,
		int controllerX,
		int controllerY,
		int controllerZ,
		boolean online,
		long capturedAt,
		int inventoryEntries,
		int cpuCount) {
}
