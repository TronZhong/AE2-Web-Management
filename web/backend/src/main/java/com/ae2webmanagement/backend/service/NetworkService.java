package com.ae2webmanagement.backend.service;

import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import com.ae2webmanagement.backend.client.ModApiClient;
import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.dto.CommandListResponse;
import com.ae2webmanagement.backend.dto.CraftAcceptedResponse;
import com.ae2webmanagement.backend.dto.CraftRequest;
import com.ae2webmanagement.backend.dto.NetworkListResponse;
import com.ae2webmanagement.backend.dto.SnapshotResponse;

/**
 * 网络查询与合成命令的用例编排：校验 {@code accessId}、调用模组客户端、
 * 把底层异常翻译为 Web API 契约错误码。
 *
 * <p>错误翻译表（错误码集合见 docs/contracts/web-api.md）：</p>
 * <ul>
 *   <li>{@code accessId} 不是合法 UUID → {@code 400 invalid_access_id}（对齐模组行为）；</li>
 *   <li>连接失败 / 超时（{@link ResourceAccessException}）→ {@code 503 mod_unreachable}；</li>
 *   <li>模组返回 401（共享密钥不匹配）→ {@code 503 mod_unavailable} + ERROR 日志；</li>
 *   <li>模组其余错误码原样透传（状态码 + {@code {"error":"code"}}）。</li>
 * </ul>
 */
@Service
public class NetworkService {

	private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

	private final ModApiClient modApiClient;

	/**
	 * @param modApiClient 访问模组的唯一入口
	 */
	public NetworkService(ModApiClient modApiClient) {
		this.modApiClient = modApiClient;
	}

	/**
	 * 网络列表（{@code GET /api/networks}）。
	 *
	 * @return 网络摘要列表响应
	 */
	public NetworkListResponse listNetworks() {
		return callMod(this.modApiClient::listNetworks);
	}

	/**
	 * 单网络完整快照（{@code GET /api/networks/{accessId}/snapshot}）。
	 *
	 * @param accessId 路径参数原值，须为合法 UUID
	 * @return 快照响应
	 */
	public SnapshotResponse getSnapshot(String accessId) {
		UUID id = parseAccessId(accessId);
		return callMod(() -> this.modApiClient.getSnapshot(id));
	}

	/**
	 * 投递合成命令（{@code POST /api/networks/{accessId}/craft}）。
	 *
	 * <p>请求体形状校验（key/amount 存在性、取值）由 controller 完成，
	 * 这里只负责 accessId 校验与转发。</p>
	 *
	 * @param accessId 路径参数原值，须为合法 UUID
	 * @param request  已通过 controller 校验的合成请求
	 * @return 202 受理响应（state=PENDING）
	 */
	public CraftAcceptedResponse submitCraft(String accessId, CraftRequest request) {
		UUID id = parseAccessId(accessId);
		return callMod(() -> this.modApiClient.submitCraft(id, request));
	}

	/**
	 * 合成命令历史（{@code GET /api/networks/{accessId}/commands}）。
	 *
	 * @param accessId 路径参数原值，须为合法 UUID
	 * @return 命令列表响应
	 */
	public CommandListResponse getCommands(String accessId) {
		UUID id = parseAccessId(accessId);
		return callMod(() -> this.modApiClient.getCommands(id));
	}

	/**
	 * 统一执行一次模组调用并做错误翻译（见类注释的错误翻译表）。
	 *
	 * @param action 实际调用模组的 lambda
	 * @param <T>    响应 DTO 类型
	 * @return 模组响应
	 */
	private <T> T callMod(Supplier<T> action) {
		try {
			return action.get();
		}
		catch (ModApiException e) {
			if ("unauthorized".equals(e.code())) {
				log.error("模组拒绝了共享密钥（X-Shared-Secret 不匹配），请检查 app.mod.shared-secret 配置", e);
				throw new ModApiException(503, "mod_unavailable", e);
			}
			throw e;
		}
		catch (ResourceAccessException e) {
			log.warn("无法连接模组本地 API（游戏未启动或模组数据口未开启）：{}", e.getMessage());
			throw new ModApiException(503, "mod_unreachable", e);
		}
	}

	/**
	 * 校验 {@code accessId} 为合法 UUID。
	 *
	 * @param accessId 路径参数原值
	 * @return 解析后的 UUID
	 */
	private static UUID parseAccessId(String accessId) {
		try {
			return UUID.fromString(accessId);
		}
		catch (IllegalArgumentException | NullPointerException e) {
			throw new ModApiException(400, "invalid_access_id");
		}
	}
}
