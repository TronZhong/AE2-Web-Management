package com.ae2webmanagement.backend.dto;

/**
 * 当前账号响应：{@code GET /api/auth/me} 返回 200（契约 v0.2「认证端点」）；
 * 前端启动时调用以恢复会话，未登录得 {@code 401 unauthorized}。
 *
 * @param username 当前登录用户名
 */
public record MeResponse(String username) {
}
