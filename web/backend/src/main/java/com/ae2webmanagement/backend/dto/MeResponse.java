package com.ae2webmanagement.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 当前账号响应：{@code GET /api/auth/me} 返回 200（契约 v0.2「认证端点」）；
 * 前端启动时调用以恢复会话，未登录得 {@code 401 unauthorized}。
 *
 * @param username   当前登录用户名
 * @param playerName 绑定的游戏内玩家名（契约 v0.11；同步账号下与 username 相同，
 *                   遗留无绑定账号为 null → 响应省略）
 */
public record MeResponse(String username, @JsonInclude(JsonInclude.Include.NON_NULL) String playerName) {
}
