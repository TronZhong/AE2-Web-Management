package com.ae2webmanagement.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ae2webmanagement.backend.dto.NetworkListResponse;
import com.ae2webmanagement.backend.dto.SnapshotResponse;
import com.ae2webmanagement.backend.service.NetworkService;

/**
 * 只读转发端点：网络列表与单网络快照。
 *
 * <p>对应 docs/contracts/web-api.md「只读端点」：</p>
 * <ul>
 *   <li>{@code GET /api/networks}</li>
 *   <li>{@code GET /api/networks/{accessId}/snapshot}
 *       （错误码 {@code network_not_found} / {@code snapshot_not_found}）</li>
 * </ul>
 *
 * <p>Controller 只做协议适配；业务编排与错误翻译在 {@link NetworkService}。</p>
 */
@RestController
@RequestMapping("/api")
public class NetworkController {

	private final NetworkService networkService;

	/**
	 * @param networkService 网络查询/合成用例服务
	 */
	public NetworkController(NetworkService networkService) {
		this.networkService = networkService;
	}

	/**
	 * 网络列表：透传模组 {@code GET /networks} 的摘要数组。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「GET /api/networks」。</p>
	 *
	 * @return 网络摘要列表
	 */
	@GetMapping("/networks")
	public NetworkListResponse networks() {
		return this.networkService.listNetworks();
	}

	/**
	 * 单网络完整快照：透传模组 {@code GET /networks/{accessId}/snapshot}。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「GET /api/networks/{accessId}/snapshot」；
	 * 网络不存在返回 {@code 404 network_not_found}（模组快照口径下为
	 * {@code snapshot_not_found}）。</p>
	 *
	 * @param accessId 网络接入 ID
	 * @return 完整快照
	 */
	@GetMapping("/networks/{accessId}/snapshot")
	public SnapshotResponse snapshot(@PathVariable String accessId) {
		return this.networkService.getSnapshot(accessId);
	}
}
