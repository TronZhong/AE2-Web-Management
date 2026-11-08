package com.ae2webmanagement.backend.dto;

import tools.jackson.databind.JsonNode;

/**
 * 合成命令请求体：{@code POST /api/grids/{accessId}/craft} 的入参。
 *
 * <p>对应 docs/contracts/web-api.md「合成命令端点」：请求体 {@code {key, amount}}，
 * {@code key} 可复用快照 {@code inventory[].key}，后端以 {@link JsonNode} 透传给模组。</p>
 *
 * <p>{@code amount} 用包装类型：缺失（null）与 0 要区分开，以便按模组行为分别返回
 * {@code missing_key_or_amount} / {@code invalid_amount}。</p>
 *
 * @param key    要合成的目标 AEKey JSON（必须是非空 object）
 * @param amount 要合成的数量（必须大于 0）
 */
public record CraftRequest(JsonNode key, Long amount) {
}
