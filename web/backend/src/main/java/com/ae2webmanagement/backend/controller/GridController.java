package com.ae2webmanagement.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.dto.GridListResponse;
import com.ae2webmanagement.backend.dto.SnapshotResponse;
import com.ae2webmanagement.backend.dto.UnlockRequest;
import com.ae2webmanagement.backend.service.AuthService;
import com.ae2webmanagement.backend.service.GridService;

/**
 * 网格查询与解锁端点：网格列表、单网格快照与网格密码解锁。
 *
 * <p>对应 docs/contracts/web-api.md：</p>
 * <ul>
 *   <li>{@code GET /api/grids} —— 所有已登录账号可见，含 {@code hasPassword}/{@code unlocked}（v0.3）</li>
 *   <li>{@code GET /api/grids/{accessId}/snapshot} —— 锁定网格返回 {@code 403 grid_locked}（v0.3）</li>
 *   <li>{@code POST /api/grids/{accessId}/unlock} —— 解锁设有网格密码的网格（v0.3）</li>
 * </ul>
 *
 * <p>Controller 只做协议适配（解锁请求体形状校验）与 accountId 提取；
 * 业务编排、访问控制与错误翻译在 {@link GridService}。</p>
 */
@RestController
@RequestMapping("/api")
public class GridController {

	private final GridService gridService;

	private final AuthService authService;

	/**
	 * @param gridService 网格查询/解锁用例服务
	 * @param authService 提供当前登录账号 ID（解锁状态按账号持久记忆）
	 */
	public GridController(GridService gridService, AuthService authService) {
		this.gridService = gridService;
		this.authService = authService;
	}

	/**
	 * 网格列表：透传模组 {@code GET /grids} 摘要并计算 {@code unlocked}。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「GET /api/grids」（v0.3 增加
	 * {@code hasPassword}/{@code unlocked} 字段）；网格列表对所有已登录账号可见。</p>
	 *
	 * @return 网格摘要列表
	 */
	@GetMapping("/grids")
	public GridListResponse grids() {
		return this.gridService.listGrids(this.authService.currentAccountId());
	}

	/**
	 * 单网格完整快照：透传模组 {@code GET /grids/{accessId}/snapshot}。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「GET /api/grids/{accessId}/snapshot」；
	 * 网格不存在返回 {@code 404 snapshot_not_found}（模组口径），
	 * 锁定且未解锁返回 {@code 403 grid_locked}（v0.3）。</p>
	 *
	 * @param accessId 网格接入 ID
	 * @return 完整快照
	 */
	@GetMapping("/grids/{accessId}/snapshot")
	public SnapshotResponse snapshot(@PathVariable String accessId) {
		return this.gridService.getSnapshot(accessId, this.authService.currentAccountId());
	}

	/**
	 * 解锁设有网格密码的网格（契约 v0.3「POST /api/grids/{accessId}/unlock」）。
	 *
	 * <p>请求体 {@code {password}}；缺失/空白密码 → {@code 400 invalid_json}；
	 * 密码不匹配 → {@code 401 invalid_password}（模组透传）；网格不存在 →
	 * {@code 404 grid_not_found}；成功 → {@code 204} 且按账号记录解锁。</p>
	 *
	 * @param accessId 网格接入 ID
	 * @param request  解锁请求体
	 * @return 204 无响应体
	 */
	@PostMapping("/grids/{accessId}/unlock")
	public ResponseEntity<Void> unlock(@PathVariable String accessId, @RequestBody UnlockRequest request) {
		if (request == null || isBlank(request.password())) {
			throw new ModApiException(400, "invalid_json");
		}
		this.gridService.unlock(accessId, this.authService.currentAccountId(), request.password());
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
