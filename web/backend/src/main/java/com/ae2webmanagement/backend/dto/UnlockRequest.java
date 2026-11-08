package com.ae2webmanagement.backend.dto;

/**
 * 解锁请求体（契约 v0.3「POST /api/grids/{accessId}/unlock」）。
 *
 * @param password 网格密码（游戏内 GUI 设置的那个）；缺失/空白由 controller 判为 invalid_json
 */
public record UnlockRequest(String password) {
}
