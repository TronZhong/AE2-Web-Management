package com.ae2webmanagement.backend.dto;

/**
 * 后端 → 模组的密码校验请求体（契约 v0.3 模组契约「POST /grids/{id}/verify-password」）。
 *
 * <p>仅供 {@code ModApiClient#verifyPassword} 序列化使用；明文密码只出现在
 * 本次 HTTP 请求体，任何持久化层不保存。</p>
 *
 * @param password 用户输入的网格密码原文
 */
public record VerifyPasswordRequest(String password) {
}
