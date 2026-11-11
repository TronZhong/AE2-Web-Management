package com.ae2webmanagement.backend.dto;

/**
 * 登录请求体：{@code POST /api/auth/login} 的入参（契约 v0.2「认证端点」；
 * v0.11 起用户名即游戏内玩家名，密码按模组同步来的 PBKDF2 哈希本地校验）。
 *
 * @param username 用户名（非空；游戏内玩家名）
 * @param password 密码（非空，明文仅出现在请求体；后端只存模组同步的 PBKDF2 哈希）
 */
public record LoginRequest(String username, String password) {
}
