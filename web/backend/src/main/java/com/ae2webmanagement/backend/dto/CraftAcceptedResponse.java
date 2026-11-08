package com.ae2webmanagement.backend.dto;

/**
 * 合成命令受理响应：{@code POST /api/grids/{accessId}/craft} 成功时返回 202。
 *
 * <p>对应 docs/contracts/web-api.md「合成命令端点」（模组契约 POST /craft 同构）；
 * {@code state} 受理时为 {@code PENDING}。</p>
 *
 * @param commandId   本次命令的 UUID
 * @param state       当前状态（受理时为 PENDING）
 * @param submittedAt 受理时间戳（epoch 毫秒）
 */
public record CraftAcceptedResponse(String commandId, String state, long submittedAt) {
}
