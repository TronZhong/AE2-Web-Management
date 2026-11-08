package com.ae2webmanagement.backend.dto;

import java.util.List;

/**
 * 网格列表响应：{@code GET /api/grids} 的返回体。
 *
 * <p>对应 docs/contracts/web-api.md「GET /api/grids」；顶层把数组包在
 * {@code grids} 字段里（与模组 GET /grids 同构），因此按非泛型 record
 * 反序列化即可，不存在泛型擦除问题。</p>
 *
 * @param grids 网格摘要列表
 */
public record GridListResponse(List<GridSummaryResponse> grids) {
}
