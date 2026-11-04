package com.ae2webmanagement.backend.dto;

/**
 * 登录请求体：{@code POST /api/auth/login} 的入参（契约 v0.2「认证端点」）。
 *
 * @param username 用户名（非空）
 * @param password 密码（非空，明文仅出现在请求体；后端只存 BCrypt 哈希）
 */
public record LoginRequest(String username, String password) {
}
