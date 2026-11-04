package com.ae2webmanagement.backend.dto;

/**
 * 网络摘要：网络列表中单个网络的条目（Web 响应 DTO，契约 v0.3）。
 *
 * <p>对应 docs/contracts/web-api.md「GET /api/networks」表的 {@code networks[]}
 * 元素；模组契约字段透传、不重命名，另增后端按账号计算的 {@code unlocked}。
 * 模组响应的反序列化使用 {@link ModNetworkSummaryResponse}（含可空
 * {@code hasPassword}），经 {@code NetworkService} 映射为本 DTO。</p>
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
 * @param hasPassword      是否设置了网格密码（v0.3；旧版模组恒 false）
 * @param unlocked         当前账号是否可访问：未设密码恒 true；设了密码则为该账号是否已解锁（v0.3）
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
		int cpuCount,
		boolean hasPassword,
		boolean unlocked) {
}
