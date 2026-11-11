package com.ae2webmanagement.backend.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import com.ae2webmanagement.backend.client.ModApiClient;
import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.dto.ModGridSummaryResponse;
import com.ae2webmanagement.backend.repository.GridAccessRepository;

/**
 * 网格状态同步服务：每 2 秒拉取模组网格列表，与内存指纹比对，变化即通过
 * {@link RealtimeEventHub} 广播事件提示前端刷新。
 *
 * <p>对应 roadmap 已完成项（SSE 实时推送）：后端无缓存、每次请求直连模组，
 * 这里在轮询的基础上补一层「状态变化主动通知」，让前端不必干等 5s/10s 轮询。
 * 事件形状只推变更提示（{@code grids-updated} / {@code grid-snapshot-updated}），
 * 前端收到后自行 refetch，契约保持简单。</p>
 *
 * <p>roadmap §11：观察到网格从模组列表消失（游戏内删除网格）时，顺带清理
 * {@code account_grid_access} 中该网格的孤儿解锁记录（「删除网格」功能引入前
 * 不存在此泄漏）。</p>
 *
 * <p><b>指纹口径</b>：只含模组侧状态——网格数量 + 每个网格的
 * {@code accessId}/{@code online}/{@code capturedAt}/{@code hasPassword}/{@code displayName}；
 * <b>不含账号相关的 {@code unlocked}</b>（解锁状态属于账号维度，改密/解锁由
 * 业务端点响应，不通过 SSE 广播）。</p>
 *
 * <p>模组不可达（游戏未启动 / 共享密钥不匹配 / 超时）时静默跳过本轮比对，
 * 保留上次指纹；模组恢复后自然能比对出差异并广播。</p>
 */
@Service
public class GridStateSyncService {

	private static final Logger log = LoggerFactory.getLogger(GridStateSyncService.class);

	/** 指纹轮询间隔（毫秒）。 */
	private static final long POLL_INTERVAL_MS = 2_000L;

	private final ModApiClient modApiClient;

	private final RealtimeEventHub eventHub;

	private final GridAccessRepository gridAccessRepository;

	/** 上次比对用的网格指纹：accessId → 模组侧状态（不含 unlocked）。 */
	private Map<String, GridFingerprint> previous = Map.of();

	/** 是否已完成首次指纹建立（首次拉取只存指纹、不广播，避免启动即推送一次全量刷新）。 */
	private boolean initialized = false;

	/**
	 * @param modApiClient         访问模组列表的唯一入口（原始模组响应，不含账号维度字段）
	 * @param eventHub             实时事件枢纽（广播变更提示）
	 * @param gridAccessRepository 已解锁网格记录表（网格消失时清理孤儿解锁记录）
	 */
	public GridStateSyncService(ModApiClient modApiClient, RealtimeEventHub eventHub,
			GridAccessRepository gridAccessRepository) {
		this.modApiClient = modApiClient;
		this.eventHub = eventHub;
		this.gridAccessRepository = gridAccessRepository;
	}

	/**
	 * 定时同步：拉取模组网格列表 → 计算指纹 → 与上次比对 → 变化即广播。
	 *
	 * <p>模组不可达时不更新指纹、不广播（下游轮询兜底），避免反复刷错误日志。</p>
	 */
	@Scheduled(fixedDelay = POLL_INTERVAL_MS)
	public void sync() {
		try {
			var response = this.modApiClient.listGrids();
			if (response == null || response.grids() == null) {
				return; // 防御：mock 或异常模组返回空，视作无可比对状态
			}
			Map<String, GridFingerprint> current = new HashMap<>();
			for (ModGridSummaryResponse summary : response.grids()) {
				current.put(summary.accessId(), new GridFingerprint(
						Boolean.TRUE.equals(summary.hasPassword()),
						summary.online(),
						summary.capturedAt(),
						summary.displayName()));
			}
			if (!this.initialized) {
				this.previous = current;
				this.initialized = true;
				return;
			}
			if (current.equals(this.previous)) {
				return;
			}
			broadcastChanges(current);
			this.previous = current;
		}
		catch (ModApiException error) {
			log.debug("SSE 同步跳过：模组调用失败（{}）", error.code());
		}
		catch (ResourceAccessException error) {
			log.debug("SSE 同步跳过：模组不可达（{}）", error.getMessage());
		}
	}

	/**
	 * 对比新旧指纹并广播差异事件。
	 *
	 * <ul>
	 *   <li>网格集合/数量变化，或某网格 {@code hasPassword}/{@code displayName}
	 *       变化 → 广播 {@code {"type":"grids-updated"}}（前端刷新网格列表）；</li>
	 *   <li>某网格 {@code online}/{@code capturedAt} 变化（快照被刷新）
	 *       → 广播 {@code {"type":"grid-snapshot-updated","accessId":...}}
	 *       （详情页刷新该网格快照）；</li>
	 *   <li>网格消失（游戏内删除）→ 清理该网格的孤儿解锁记录（roadmap §11）。</li>
	 * </ul>
	 *
	 * @param current 本次拉取的指纹
	 */
	private void broadcastChanges(Map<String, GridFingerprint> current) {
		boolean listChanged = false;
		for (Map.Entry<String, GridFingerprint> entry : current.entrySet()) {
			GridFingerprint now = entry.getValue();
			GridFingerprint before = this.previous.get(entry.getKey());
			if (before == null) {
				listChanged = true; // 新网格出现
			}
			else if (before.hasPassword() != now.hasPassword()
					|| !before.displayName().equals(now.displayName())) {
				listChanged = true; // 密码/名称这类「列表级」信息变化
			}
			if (before == null
					|| before.online() != now.online()
					|| before.capturedAt() != now.capturedAt()) {
				// 快照被刷新（在线态/采样时间变化）→ 推详情页刷新该网格
				this.eventHub.broadcast(Map.of(
						"type", "grid-snapshot-updated",
						"accessId", entry.getKey()));
			}
		}
		for (String accessId : this.previous.keySet()) {
			if (!current.containsKey(accessId)) {
				listChanged = true; // 网格被移除
				// 级联清理孤儿解锁记录（roadmap §11）：删除网格后 unlock 记录已无意义
				this.gridAccessRepository.deleteByAccessId(accessId);
			}
		}
		if (listChanged) {
			this.eventHub.broadcast(Map.of("type", "grids-updated"));
		}
	}

	/**
	 * 单个网格的模组侧状态指纹（用于 SSE 变更比对）。
	 *
	 * @param hasPassword 是否设置了网格密码（模组侧口径，不含账号维度）
	 * @param online      最近一次快照的在线态
	 * @param capturedAt  最近一次快照采样时间（epoch 毫秒）
	 * @param displayName 显示名
	 */
	private record GridFingerprint(
			boolean hasPassword,
			boolean online,
			long capturedAt,
			String displayName) {
	}
}
