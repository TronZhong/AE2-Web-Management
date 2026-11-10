package com.ae2webmanagement.backend.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import com.ae2webmanagement.backend.client.ModApiClient;
import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.dto.ModGridListResponse;
import com.ae2webmanagement.backend.dto.ModGridSummaryResponse;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GridStateSyncService} 单元测试：指纹比对与事件广播语义
 * （roadmap 已完成项：SSE 实时推送）。
 *
 * <p>覆盖：无变化不广播；新网格出现 → {@code grids-updated}；快照被刷新
 * （{@code capturedAt}/{@code online} 变化）→ 该网格的 {@code grid-snapshot-updated}；
 * 名称/密码变化 → {@code grids-updated}；网格移除 → {@code grids-updated}；
 * 模组不可达静默跳过。</p>
 */
@ExtendWith(MockitoExtension.class)
class GridStateSyncServiceTests {

	private static final String ID_A = "0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001";
	private static final String ID_B = "0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a002";

	@Mock
	private ModApiClient modApiClient;

	@Mock
	private RealtimeEventHub eventHub;

	@InjectMocks
	private GridStateSyncService service;

	/** 网格状态完全不变 → 不广播任何事件。 */
	@Test
	void unchangedFingerprintBroadcastsNothing() {
		when(this.modApiClient.listGrids())
			.thenReturn(new ModGridListResponse(List.of(summary(ID_A, false, true, 1_000L, "主基地"))));

		this.service.sync();
		this.service.sync();

		verify(this.eventHub, never()).broadcast(anyMap());
	}

	/** 新网格出现 → 广播 grids-updated + 该网格的 grid-snapshot-updated（共 2 次广播）。 */
	@Test
	void newGridBroadcastsGridsUpdated() {
		when(this.modApiClient.listGrids())
			.thenReturn(new ModGridListResponse(List.of(summary(ID_A, false, true, 1_000L, "主基地"))))
			.thenReturn(new ModGridListResponse(List.of(
					summary(ID_A, false, true, 1_000L, "主基地"),
					summary(ID_B, false, true, 2_000L, "农场"))));

		this.service.sync();
		this.service.sync();

		verify(this.eventHub, org.mockito.Mockito.times(2)).broadcast(anyMap());
	}

	/** 某网格快照被刷新（capturedAt 变化）→ 广播该网格的 grid-snapshot-updated。 */
	@Test
	void snapshotRefreshBroadcastsSnapshotUpdated() {
		when(this.modApiClient.listGrids())
			.thenReturn(new ModGridListResponse(List.of(summary(ID_A, false, true, 1_000L, "主基地"))))
			.thenReturn(new ModGridListResponse(List.of(summary(ID_A, false, true, 2_000L, "主基地"))));

		this.service.sync();
		this.service.sync();

		verify(this.eventHub).broadcast(new java.util.HashMap<>(java.util.Map.of(
				"type", "grid-snapshot-updated", "accessId", ID_A)));
	}

	/** 显示名变化（列表级信息）→ 广播 grids-updated。 */
	@Test
	void renamedGridBroadcastsGridsUpdated() {
		when(this.modApiClient.listGrids())
			.thenReturn(new ModGridListResponse(List.of(summary(ID_A, false, true, 1_000L, "旧名"))))
			.thenReturn(new ModGridListResponse(List.of(summary(ID_A, false, true, 1_000L, "新名"))));

		this.service.sync();
		this.service.sync();

		verify(this.eventHub).broadcast(new java.util.HashMap<>(java.util.Map.of("type", "grids-updated")));
	}

	/** 网格被移除 → 广播 grids-updated。 */
	@Test
	void removedGridBroadcastsGridsUpdated() {
		when(this.modApiClient.listGrids())
			.thenReturn(new ModGridListResponse(List.of(
					summary(ID_A, false, true, 1_000L, "主基地"),
					summary(ID_B, false, true, 2_000L, "农场"))))
			.thenReturn(new ModGridListResponse(List.of(summary(ID_A, false, true, 1_000L, "主基地"))));

		this.service.sync();
		this.service.sync();

		verify(this.eventHub).broadcast(new java.util.HashMap<>(java.util.Map.of("type", "grids-updated")));
	}

	/** 模组不可达（连接失败）→ 静默跳过，不广播不抛异常。 */
	@Test
	void modUnreachableSkipsSilently() {
		when(this.modApiClient.listGrids()).thenThrow(new ResourceAccessException("connect timed out"));

		this.service.sync();

		verify(this.eventHub, never()).broadcast(anyMap());
	}

	/** 模组业务错误（如共享密钥被拒）→ 静默跳过，不广播不抛异常。 */
	@Test
	void modApiErrorSkipsSilently() {
		when(this.modApiClient.listGrids()).thenThrow(new ModApiException(503, "mod_unavailable"));

		this.service.sync();

		verify(this.eventHub, never()).broadcast(anyMap());
	}

	/**
	 * 构造模组侧网格摘要。
	 *
	 * @param accessId    网格接入 ID
	 * @param hasPassword 是否设密码
	 * @param online      在线态
	 * @param capturedAt  采样时间
	 * @param displayName 显示名
	 */
	private static ModGridSummaryResponse summary(
			String accessId, boolean hasPassword, boolean online, long capturedAt, String displayName) {
		return new ModGridSummaryResponse(accessId, displayName, "minecraft:overworld",
				128, 64, -256, online, capturedAt, 17, 2, hasPassword, capturedAt);
	}
}
