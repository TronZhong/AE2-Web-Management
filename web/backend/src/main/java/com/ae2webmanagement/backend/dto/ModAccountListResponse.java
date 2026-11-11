package com.ae2webmanagement.backend.dto;

import java.util.List;

/**
 * 模组 {@code GET /accounts} 响应的反序列化目标（模组契约「账号 API」，0.1.0 起）。
 *
 * <p>对应 docs/contracts/index.md「账号 API」；字段透传、不重命名。旧版模组
 * （&lt;0.1.0）无该端点 → 404，由 {@code AccountSyncService} 静默跳过（向后兼容）。</p>
 *
 * @param accounts 模组账号记录列表
 */
public record ModAccountListResponse(List<ModAccountEntry> accounts) {
}
