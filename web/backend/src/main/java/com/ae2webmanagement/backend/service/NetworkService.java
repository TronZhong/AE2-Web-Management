package com.ae2webmanagement.backend.service;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import com.ae2webmanagement.backend.client.ModApiClient;
import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.domain.GridAccess;
import com.ae2webmanagement.backend.dto.CommandListResponse;
import com.ae2webmanagement.backend.dto.CraftAcceptedResponse;
import com.ae2webmanagement.backend.dto.CraftRequest;
import com.ae2webmanagement.backend.dto.ModNetworkListResponse;
import com.ae2webmanagement.backend.dto.ModNetworkSummaryResponse;
import com.ae2webmanagement.backend.dto.NetworkListResponse;
import com.ae2webmanagement.backend.dto.NetworkSummaryResponse;
import com.ae2webmanagement.backend.dto.SnapshotResponse;
import com.ae2webmanagement.backend.repository.GridAccessRepository;

/**
 * 网络查询与合成命令的用例编排：校验 {@code accessId}、调用模组客户端、
 * 把底层异常翻译为 Web API 契约错误码，并执行网格密码访问控制（契约 v0.3）。
 *
 * <p>错误翻译表（错误码集合见 docs/contracts/web-api.md）：</p>
 * <ul>
 *   <li>{@code accessId} 不是合法 UUID → {@code 400 invalid_access_id}（对齐模组行为）；</li>
 *   <li>连接失败 / 超时（{@link ResourceAccessException}）→ {@code 503 mod_unreachable}；</li>
 *   <li>模组返回 401（共享密钥不匹配）→ {@code 503 mod_unavailable} + ERROR 日志；</li>
 *   <li>模组其余错误码原样透传（状态码 + {@code {"error":"code"}}），
 *       含网格密码的 {@code invalid_password}（401）。</li>
 * </ul>
 *
 * <p>网格访问控制（v0.3）：网络设有密码且当前账号未解锁时，
 * snapshot / craft / commands 在转发模组前拒绝，返回 {@code 403 network_locked}；
 * 解锁状态（{@code account_grid_access} 表）按账号持久记忆。
 * 访问控制检查通过 {@code GET /networks} 列表完成——网络不在列表中时不拦截，
 * 让模组对未知网络返回精确的 404 错误码（snapshot_not_found / network_not_found）。</p>
 */
@Service
public class NetworkService {

	private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

	private final ModApiClient modApiClient;

	private final GridAccessRepository gridAccessRepository;

	/**
	 * @param modApiClient         访问模组的唯一入口
	 * @param gridAccessRepository 已解锁网格记录表访问
	 */
	public NetworkService(ModApiClient modApiClient, GridAccessRepository gridAccessRepository) {
		this.modApiClient = modApiClient;
		this.gridAccessRepository = gridAccessRepository;
	}

	/**
	 * 网络列表（{@code GET /api/networks}）：对所有已登录账号可见，
	 * 含未解锁的锁定网络（前端据此显示锁定徽标）。
	 *
	 * <p>在模组摘要基础上计算 {@code unlocked}（未设密码恒 true；设了密码则为
	 * 该账号是否已有解锁记录）。另含撤销语义：观察到网络密码被清除
	 * （{@code hasPassword=false}）时删除该网络全部解锁记录，
	 * 避免重新设密后旧账号凭残留记录免密进入。</p>
	 *
	 * @param accountId 当前登录账号 ID
	 * @return 补齐 {@code hasPassword}/{@code unlocked} 后的网络摘要列表响应
	 */
	public NetworkListResponse listNetworks(long accountId) {
		ModNetworkListResponse response = callMod(this.modApiClient::listNetworks);
		return new NetworkListResponse(response.networks().stream()
			.map((summary) -> toAccessibleSummary(summary, accountId))
			.toList());
	}

	/**
	 * 单网络完整快照（{@code GET /api/networks/{accessId}/snapshot}）。
	 *
	 * @param accessId  路径参数原值，须为合法 UUID
	 * @param accountId 当前登录账号 ID（访问控制用）
	 * @return 快照响应
	 */
	public SnapshotResponse getSnapshot(String accessId, long accountId) {
		UUID id = parseAccessId(accessId);
		ensureAccessible(id, accountId);
		return callMod(() -> this.modApiClient.getSnapshot(id));
	}

	/**
	 * 投递合成命令（{@code POST /api/networks/{accessId}/craft}）。
	 *
	 * <p>请求体形状校验（key/amount 存在性、取值）由 controller 完成，
	 * 这里负责 accessId 校验、访问控制与转发。</p>
	 *
	 * @param accessId  路径参数原值，须为合法 UUID
	 * @param request   已通过 controller 校验的合成请求
	 * @param accountId 当前登录账号 ID（访问控制用）
	 * @return 202 受理响应（state=PENDING）
	 */
	public CraftAcceptedResponse submitCraft(String accessId, CraftRequest request, long accountId) {
		UUID id = parseAccessId(accessId);
		ensureAccessible(id, accountId);
		return callMod(() -> this.modApiClient.submitCraft(id, request));
	}

	/**
	 * 合成命令历史（{@code GET /api/networks/{accessId}/commands}）。
	 *
	 * @param accessId  路径参数原值，须为合法 UUID
	 * @param accountId 当前登录账号 ID（访问控制用）
	 * @return 命令列表响应
	 */
	public CommandListResponse getCommands(String accessId, long accountId) {
		UUID id = parseAccessId(accessId);
		ensureAccessible(id, accountId);
		return callMod(() -> this.modApiClient.getCommands(id));
	}

	/**
	 * 解锁网络（{@code POST /api/networks/{accessId}/unlock}，契约 v0.3）。
	 *
	 * <p>转发模组 {@code verify-password}：204（未设密码或密码正确）→ 按账号
	 * 记录解锁（幂等，重复 unlock 只保留一条记录）；模组 401 invalid_password /
	 * 404 network_not_found 原样透传且不写记录。解锁状态持久化在
	 * {@code account_grid_access} 表，跨会话生效。</p>
	 *
	 * @param accessId  路径参数原值，须为合法 UUID
	 * @param accountId 当前登录账号 ID
	 * @param password  用户输入的网络密码原文（已由 controller 确保非空白）
	 */
	public void unlock(String accessId, long accountId, String password) {
		UUID id = parseAccessId(accessId);
		callMod(() -> {
			this.modApiClient.verifyPassword(id, password);
			return null;
		});
		if (!this.gridAccessRepository.existsByAccountIdAndAccessId(accountId, id.toString())) {
			GridAccess access = new GridAccess();
			access.setAccountId(accountId);
			access.setAccessId(id.toString());
			access.setUnlockedAt(Instant.now());
			this.gridAccessRepository.save(access);
		}
	}

	/**
	 * 网格访问控制检查：网络设有密码且当前账号未解锁 → {@code 403 network_locked}。
	 *
	 * <p>网络不在模组列表中时不拦截（hasPassword 按 false 处理），把 404 语义
	 * 留给模组端点本身（snapshot_not_found / network_not_found），与直连模组时
	 * 的错误口径一致。旧版模组无 hasPassword 字段 → 一律放行（向后兼容）。</p>
	 *
	 * @param id        已解析的网络接入 ID
	 * @param accountId 当前登录账号 ID
	 */
	private void ensureAccessible(UUID id, long accountId) {
		ModNetworkListResponse response = callMod(this.modApiClient::listNetworks);
		boolean locked = response.networks().stream()
			.filter((summary) -> summary.accessId().equals(id.toString()))
			.findFirst()
			.map((summary) -> Boolean.TRUE.equals(summary.hasPassword())
					&& !this.gridAccessRepository.existsByAccountIdAndAccessId(accountId, summary.accessId()))
			.orElse(false);
		if (locked) {
			throw new ModApiException(403, "network_locked");
		}
	}

	/**
	 * 把模组摘要映射为带访问控制字段的 Web 摘要。
	 *
	 * <p>{@code unlocked} = 未设密码（含旧版模组缺字段的情况，反序列化为 null）
	 * 或该账号已有解锁记录；密码被清除的网络顺带撤销全部解锁记录。</p>
	 *
	 * @param summary   模组返回的原始摘要（hasPassword 可空，null 视为未设密码）
	 * @param accountId 当前登录账号 ID
	 * @return 补齐 {@code unlocked} 的 Web 摘要
	 */
	private NetworkSummaryResponse toAccessibleSummary(ModNetworkSummaryResponse summary, long accountId) {
		boolean hasPassword = Boolean.TRUE.equals(summary.hasPassword());
		if (!hasPassword) {
			this.gridAccessRepository.deleteByAccessId(summary.accessId());
		}
		boolean unlocked = !hasPassword
				|| this.gridAccessRepository.existsByAccountIdAndAccessId(accountId, summary.accessId());
		return new NetworkSummaryResponse(summary.accessId(), summary.displayName(), summary.dimension(),
				summary.controllerX(), summary.controllerY(), summary.controllerZ(),
				summary.online(), summary.capturedAt(), summary.inventoryEntries(), summary.cpuCount(),
				hasPassword, unlocked);
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
