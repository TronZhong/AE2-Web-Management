package com.ae2webmanagement.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.dto.CommandListResponse;
import com.ae2webmanagement.backend.dto.CraftAcceptedResponse;
import com.ae2webmanagement.backend.dto.CraftRequest;
import com.ae2webmanagement.backend.service.AuthService;
import com.ae2webmanagement.backend.service.GridService;

/**
 * 合成命令转发端点：投递合成命令与查询命令状态。
 *
 * <p>对应 docs/contracts/web-api.md「合成命令端点」：</p>
 * <ul>
 *   <li>{@code POST /api/grids/{accessId}/craft} — 请求体 {@code {key, amount}}，
 *       成功返回 {@code 202 {commandId, state, submittedAt}}（state=PENDING）</li>
 *   <li>{@code GET /api/grids/{accessId}/commands} — 命令状态列表
 *       （PENDING / SUBMITTED / FAILED）</li>
 *   <li>{@code DELETE /api/grids/{accessId}/commands/{commandId}} — 删除命令记录
 *       （v0.9：删 PENDING 即撤销，删终态仅清理历史）</li>
 * </ul>
 *
 * <p>Controller 只做协议适配：请求体形状校验与模组行为对齐
 * （{@code missing_key_or_amount} / {@code invalid_amount} / {@code invalid_key}），
 * 编排、网格访问控制（v0.3 grid_locked）与错误翻译在 {@link GridService}。</p>
 */
@RestController
@RequestMapping("/api")
public class CraftController {

	private final GridService gridService;

	private final AuthService authService;

	/**
	 * @param gridService 网格查询/合成用例服务
	 * @param authService 提供当前登录账号 ID（网格访问控制按账号判定）
	 */
	public CraftController(GridService gridService, AuthService authService) {
		this.gridService = gridService;
		this.authService = authService;
	}

	/**
	 * 投递合成命令：校验请求体后透传模组 {@code POST /grids/{accessId}/craft}。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「合成命令端点」；校验规则与模组
	 * {@code LocalDataServer} 一致（缺失 → {@code missing_key_or_amount}，
	 * amount ≤ 0 → {@code invalid_amount}，key 非空 object → {@code invalid_key}）。</p>
	 *
	 * @param accessId 网格接入 ID
	 * @param request  合成请求 {@code {key, amount}}
	 * @return 202 受理响应
	 */
	@PostMapping("/grids/{accessId}/craft")
	public ResponseEntity<CraftAcceptedResponse> craft(@PathVariable String accessId, @RequestBody CraftRequest request) {
		validateCraftRequest(request);
		return ResponseEntity.accepted()
			.body(this.gridService.submitCraft(accessId, request, this.authService.currentAccountId()));
	}

	/**
	 * 命令状态列表：透传模组 {@code GET /grids/{accessId}/commands}。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「合成命令端点」。</p>
	 *
	 * @param accessId 网格接入 ID
	 * @return 命令条目列表
	 */
	@GetMapping("/grids/{accessId}/commands")
	public CommandListResponse commands(@PathVariable String accessId) {
		return this.gridService.getCommands(accessId, this.authService.currentAccountId());
	}

	/**
	 * 删除命令记录（契约 v0.9「DELETE /api/grids/{accessId}/commands/{commandId}」）。
	 *
	 * <p>后端纯转发：模组在主线程原子删除——删除 PENDING 记录即撤销（不会提交 AE2），
	 * 删除 SUBMITTED/FAILED 记录仅清理历史。成功 204 无响应体；命令不存在 404
	 * {@code command_not_found}；网格不存在 404 {@code grid_not_found}；锁定网格 403
	 * {@code grid_locked}；路径参数非法 400 {@code invalid_access_id} / {@code invalid_command_id}。</p>
	 *
	 * @param accessId  网格接入 ID
	 * @param commandId 命令 ID
	 * @return 204 无响应体
	 */
	@DeleteMapping("/grids/{accessId}/commands/{commandId}")
	public ResponseEntity<Void> deleteCommand(@PathVariable String accessId, @PathVariable String commandId) {
		this.gridService.deleteCommand(accessId, commandId, this.authService.currentAccountId());
		return ResponseEntity.noContent().build();
	}

	/**
	 * 校验合成请求体形状，与模组 {@code POST /craft} 的校验行为逐条对齐。
	 *
	 * @param request 请求体 DTO（Jackson 反序列化结果；缺失字段为 null）
	 */
	private static void validateCraftRequest(CraftRequest request) {
		if (request.key() == null || request.amount() == null) {
			throw new ModApiException(400, "missing_key_or_amount");
		}
		if (request.amount() <= 0) {
			throw new ModApiException(400, "invalid_amount");
		}
		if (!request.key().isObject() || request.key().isEmpty()) {
			throw new ModApiException(400, "invalid_key");
		}
	}
}
