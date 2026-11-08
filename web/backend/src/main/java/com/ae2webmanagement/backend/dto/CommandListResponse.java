package com.ae2webmanagement.backend.dto;

import java.util.List;

/**
 * 合成命令列表响应：{@code GET /api/grids/{accessId}/commands} 的返回体。
 *
 * <p>对应 docs/contracts/web-api.md「合成命令端点」；顶层把数组包在
 * {@code commands} 字段里（与模组契约同构），按非泛型 record 反序列化即可。</p>
 *
 * @param commands 命令条目列表
 */
public record CommandListResponse(List<CommandEntryResponse> commands) {
}
