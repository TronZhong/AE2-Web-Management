package com.ae2webmanagement.backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.dto.LoginRequest;
import com.ae2webmanagement.backend.dto.LoginResponse;
import com.ae2webmanagement.backend.dto.MeResponse;
import com.ae2webmanagement.backend.service.AuthService;

/**
 * 账号认证端点（契约 v0.2「认证端点」）：登录（唯一免认证端点）、注销、当前账号。
 *
 * <p>Controller 只做协议适配：请求体形状校验（缺失字段 → {@code invalid_json}），
 * 凭证校验与会话管理在 {@link AuthService}；未登录访问由 Sa-Token 拦截器拦截，
 * 经 {@link GlobalExceptionHandler} 输出 {@code 401 {"error":"unauthorized"}}。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	/**
	 * @param authService 登录/注销/当前账号用例服务
	 */
	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/**
	 * 登录：校验用户名密码，成功后建立会话（写 satoken Cookie）。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「POST /api/auth/login」；
	 * 凭证错误 → 401 unauthorized，缺失字段 → 400 invalid_json。</p>
	 *
	 * @param request 登录请求体
	 * @return 200 受理响应（token 名、token 值、用户名回显）
	 */
	@PostMapping("/login")
	public LoginResponse login(@RequestBody LoginRequest request) {
		if (request == null || isBlank(request.username()) || isBlank(request.password())) {
			throw new ModApiException(400, "invalid_json");
		}
		return this.authService.login(request.username().trim(), request.password());
	}

	/**
	 * 注销当前会话：使 token 失效并清除 Cookie（契约「POST /api/auth/logout」）。
	 *
	 * @return 200 空对象
	 */
	@PostMapping("/logout")
	public Map<String, Object> logout() {
		this.authService.logout();
		return Map.of();
	}

	/**
	 * 当前登录账号：前端启动时调用以恢复会话（契约「GET /api/auth/me」）。
	 *
	 * @return 200 当前用户名
	 */
	@GetMapping("/me")
	public MeResponse me() {
		return this.authService.me();
	}

	/**
	 * @param value 字符串原值
	 * @return 是否为 null / 空白
	 */
	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
