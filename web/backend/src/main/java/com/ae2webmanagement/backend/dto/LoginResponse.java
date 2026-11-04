package com.ae2webmanagement.backend.dto;

/**
 * 登录成功响应：{@code POST /api/auth/login} 返回 200（契约 v0.2「认证端点」）。
 *
 * <p>{@code tokenValue} 同时已由 Sa-Token 写入 {@code satoken} Cookie；
 * 非 Cookie 客户端可用请求头 {@code satoken: <tokenValue>} 等价传递。</p>
 *
 * @param tokenName  token 请求头/Cookie 名（固定 {@code satoken}）
 * @param tokenValue 本次会话 token
 * @param username   登录成功的用户名（回显）
 */
public record LoginResponse(String tokenName, String tokenValue, String username) {
}
