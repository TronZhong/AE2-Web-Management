package com.ae2webmanagement.backend.dto;

import java.util.List;

/**
 * 样板输入槽：快照 {@code patterns[].inputs[]} 元素。
 *
 * <p>对应 docs/contracts/web-api.md 的 {@code inputs[]} 字段表；
 * 口径为 AE2 {@code IPatternDetails}。</p>
 *
 * @param possibleInputs  该槽候选物品列表，均为 {key, amount}
 * @param multiplier      槽位倍率；实际需求量 = possibleInputs[i].amount × multiplier
 */
public record PatternInputEntry(List<StackEntry> possibleInputs, int multiplier) {
}
