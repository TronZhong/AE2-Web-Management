package com.ae2webmanagement.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/**
 * AEKey 通用条目：库存、样板输出等处共用的 {@code {key, amount, nameKey?}} 形状。
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
 * <p>{@code nameKey} 是模组采集时附加的可选物品翻译键（roadmap.md §4，如
 * {@code block.minecraft.stone}），供前端查中文语言表；后端只透传不解释。<br>
 * 旧版模组没有该字段 → 反序列化为 null，序列化时经 {@code @JsonInclude(NON_NULL)}
 * 省略，Web 响应仍保持「可选字段」形状（向前兼容）。</p>
 *
 * @param key     AEKey 的 JSON 表示
 * @param amount  聚合数量
 * @param nameKey 可选的物品翻译键；无（旧版模组 / 无法确定）时为 null
 */
public record StackEntry(
        JsonNode key,
        long amount,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nameKey) {
}
