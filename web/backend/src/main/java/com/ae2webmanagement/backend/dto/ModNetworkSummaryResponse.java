package com.ae2webmanagement.backend.dto;

/**
 * 模组网络摘要（模组契约 {@code GET /networks} 的单个条目，反序列化目标）。
 *
 * <p>对应 docs/contracts/index.md「GET /networks」；字段透传、不重命名。
 * 与 web 侧 {@link NetworkSummaryResponse} 的差异：无 {@code unlocked}
 * （后端按账号计算），{@code hasPassword} 为 {@code Boolean} ——
 * 旧版模组（&lt;0.0.6）无该字段时反序列化为 {@code null}，
 * 由 {@code NetworkService} 归一为「无密码 = 公开」（向后兼容）。</p>
 *
 * @param accessId         网络接入 ID（UUID）
 * @param displayName      显示名（游戏内注册时命名）
 * @param dimension        控制器所在维度
 * @param controllerX      ME 控制器 X 坐标
 * @param controllerY      ME 控制器 Y 坐标
 * @param controllerZ      ME 控制器 Z 坐标
 * @param online           最近一次快照的在线态
 * @param capturedAt       最近一次快照采样时间（epoch 毫秒）；从未采样为 0
 * @param inventoryEntries 库存物品种类数（非数量）
 * @param cpuCount         合成 CPU 数量
 * @param hasPassword      是否设置了网格密码（v0.3；旧版模组为 null → 按公开处理）
 */
public record ModNetworkSummaryResponse(
		String accessId,
		String displayName,
		String dimension,
		int controllerX,
		int controllerY,
		int controllerZ,
		boolean online,
		long capturedAt,
		int inventoryEntries,
		int cpuCount,
		Boolean hasPassword) {
}
