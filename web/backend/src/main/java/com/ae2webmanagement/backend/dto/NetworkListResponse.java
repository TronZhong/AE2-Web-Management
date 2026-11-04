package com.ae2webmanagement.backend.dto;

import java.util.List;

/**
 * 网络列表响应：{@code GET /api/networks} 的返回体。
 *
 * <p>对应 docs/contracts/web-api.md「GET /api/networks」；顶层把数组包在
 * {@code networks} 字段里（与模组 GET /networks 同构），因此按非泛型 record
 * 反序列化即可，不存在泛型擦除问题。</p>
 *
 * @param networks 网络摘要列表
 */
public record NetworkListResponse(List<NetworkSummaryResponse> networks) {
}
