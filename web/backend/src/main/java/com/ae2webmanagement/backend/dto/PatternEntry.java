package com.ae2webmanagement.backend.dto;

import java.util.List;

/**
 * 样板摘要：快照 {@code patterns[]} 元素。
 *
 * <p>对应 docs/contracts/web-api.md「GET /api/networks/{accessId}/snapshot」的
 * {@code patterns[]} 字段表（口径：AE2 {@code IPatternDetails}，不含可发射物品，
 * 见 docs/design.md §7）。</p>
 *
 * @param primaryOutput 主输出 {key, amount}，可直接作为 POST /craft 的 key
 * @param outputs       全部样板输出，均为 {key, amount}
 * @param inputs        样板输入槽列表
 */
public record PatternEntry(
		StackEntry primaryOutput,
		List<StackEntry> outputs,
		List<PatternInputEntry> inputs) {
}
