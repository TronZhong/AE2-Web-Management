package com.ae2webmanagement.backend.dto;

import tools.jackson.databind.JsonNode;

/**
 * AEKey 通用条目：库存、样板输出等处共用的 {@code {key, amount}} 形状。
 *
 * <p>对应 docs/contracts/web-api.md「单网格快照」中 {@code inventory[]}、
 * {@code patterns[].primaryOutput}、{@code patterns[].outputs}、
 * {@code inputs[].possibleInputs[]} 的元素类型（模组契约同构）。</p>
 *
 * <p>{@code key} 是 AEKey 通用 NBT 转换后的 JSON object（物品形如
 * {@code {"id":"minecraft:stone"}}）；按既定决策用 {@link JsonNode} 透传，
 * 后端不建强类型模型、不改写字段。</p>
 *
 * <p>{@code amount} 为聚合数量，按契约以 JSON number 透传（long 值超过 2^53
 * 理论上丢精度，实际库存量达不到，契约已加备注）。</p>
 *
 * @param key    AEKey 的 JSON 表示
 * @param amount 聚合数量
 */
public record StackEntry(JsonNode key, long amount) {
}
