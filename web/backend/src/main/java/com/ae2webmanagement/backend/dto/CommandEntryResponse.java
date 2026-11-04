package com.ae2webmanagement.backend.dto;

import tools.jackson.databind.JsonNode;

/**
 * 合成命令条目：{@code GET /api/networks/{accessId}/commands} 的 {@code commands[]}
 * 元素（模组契约同构）。
 *
 * <p>对应 docs/contracts/web-api.md「合成命令端点」；状态为
 * {@code PENDING / SUBMITTED / FAILED}。</p>
 *
 * @param commandId   命令 UUID
 * @param accessId    归属网络接入 ID
 * @param state       当前状态：PENDING / SUBMITTED / FAILED
 * @param submittedAt 受理时间戳（epoch 毫秒）
 * @param result      执行结果：SUBMITTED 时为 AE2 craftingId，FAILED 时为原因，PENDING 时为 null
 * @param amount      请求合成的数量
 * @param target      目标 AEKey 的 JSON，以 {@link JsonNode} 透传（同 {@link StackEntry#key()}）
 */
public record CommandEntryResponse(
		String commandId,
		String accessId,
		String state,
		long submittedAt,
		String result,
		long amount,
		JsonNode target) {
}
