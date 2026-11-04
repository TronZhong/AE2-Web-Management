package com.ae2webmanagement.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.dto.NetworkListResponse;
import com.ae2webmanagement.backend.dto.SnapshotResponse;
import com.ae2webmanagement.backend.dto.UnlockRequest;
import com.ae2webmanagement.backend.service.AuthService;
import com.ae2webmanagement.backend.service.NetworkService;

/**
 * 网络查询与解锁端点：网络列表、单网络快照与网格密码解锁。
 *
 * <p>对应 docs/contracts/web-api.md：</p>
 * <ul>
 *   <li>{@code GET /api/networks} —— 所有已登录账号可见，含 {@code hasPassword}/{@code unlocked}（v0.3）</li>
 *   <li>{@code GET /api/networks/{accessId}/snapshot} —— 锁定网络返回 {@code 403 network_locked}（v0.3）</li>
 *   <li>{@code POST /api/networks/{accessId}/unlock} —— 解锁设有网格密码的网络（v0.3）</li>
 * </ul>
 *
 * <p>Controller 只做协议适配（解锁请求体形状校验）与 accountId 提取；
 * 业务编排、访问控制与错误翻译在 {@link NetworkService}。</p>
 */
@RestController
@RequestMapping("/api")
public class NetworkController {

	private final NetworkService networkService;

	private final AuthService authService;

	/**
	 * @param networkService 网络查询/解锁用例服务
	 * @param authService    提供当前登录账号 ID（解锁状态按账号持久记忆）
	 */
	public NetworkController(NetworkService networkService, AuthService authService) {
		this.networkService = networkService;
		this.authService = authService;
	}

	/**
	 * 网络列表：透传模组 {@code GET /networks} 摘要并计算 {@code unlocked}。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「GET /api/networks」（v0.3 增加
	 * {@code hasPassword}/{@code unlocked} 字段）；网络列表对所有已登录账号可见。</p>
	 *
	 * @return 网络摘要列表
	 */
	@GetMapping("/networks")
	public NetworkListResponse networks() {
		return this.networkService.listNetworks(this.authService.currentAccountId());
	}

	/**
	 * 单网络完整快照：透传模组 {@code GET /networks/{accessId}/snapshot}。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「GET /api/networks/{accessId}/snapshot」；
	 * 网络不存在返回 {@code 404 snapshot_not_found}（模组口径），
	 * 锁定且未解锁返回 {@code 403 network_locked}（v0.3）。</p>
	 *
	 * @param accessId 网络接入 ID
	 * @return 完整快照
	 */
	@GetMapping("/networks/{accessId}/snapshot")
	public SnapshotResponse snapshot(@PathVariable String accessId) {
		return this.networkService.getSnapshot(accessId, this.authService.currentAccountId());
	}

	/**
	 * 解锁设有网格密码的网络（契约 v0.3「POST /api/networks/{accessId}/unlock」）。
	 *
	 * <p>请求体 {@code {password}}；缺失/空白密码 → {@code 400 invalid_json}；
	 * 密码不匹配 → {@code 401 invalid_password}（模组透传）；网络不存在 →
	 * {@code 404 network_not_found}；成功 → {@code 204} 且按账号记录解锁。</p>
	 *
	 * @param accessId 网络接入 ID
	 * @param request  解锁请求体
	 * @return 204 无响应体
	 */
	@PostMapping("/networks/{accessId}/unlock")
	public ResponseEntity<Void> unlock(@PathVariable String accessId, @RequestBody UnlockRequest request) {
		if (request == null || isBlank(request.password())) {
			throw new ModApiException(400, "invalid_json");
		}
		this.networkService.unlock(accessId, this.authService.currentAccountId(), request.password());
		return ResponseEntity.noContent().build();
	}

	/**
	 * @param value 字符串原值
	 * @return 是否为 null / 空白
	 */
	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
