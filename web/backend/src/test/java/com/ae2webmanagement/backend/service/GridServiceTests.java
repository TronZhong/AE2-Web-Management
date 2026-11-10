package com.ae2webmanagement.backend.service;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import com.ae2webmanagement.backend.client.ModApiClient;
import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.domain.GridAccess;
import com.ae2webmanagement.backend.dto.GridListResponse;
import com.ae2webmanagement.backend.dto.GridSummaryResponse;
import com.ae2webmanagement.backend.dto.ModGridListResponse;
import com.ae2webmanagement.backend.dto.ModGridSummaryResponse;
import com.ae2webmanagement.backend.repository.GridAccessRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link GridService} 测试：错误翻译表（accessId 校验、mod_unreachable、
 * mod_unavailable、其余错误码透传）与网格访问控制（v0.3 grid_locked、
 * unlocked 计算、旧版模组向后兼容、unlock 记录）。
 */
@ExtendWith(MockitoExtension.class)
class GridServiceTests {

	private static final String ACCESS_ID = "0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001";

	private static final String COMMAND_ID = "5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100";

	private static final long ACCOUNT_ID = 42L;

	@Mock
	private ModApiClient modApiClient;

	@Mock
	private GridAccessRepository gridAccessRepository;

	@InjectMocks
	private GridService service;

	/** accessId 非法 → 400 invalid_access_id，且不触达模组客户端。 */
	@Test
	void invalidAccessIdMapsTo400AndSkipsModCall() {
		assertThatThrownBy(() -> this.service.getSnapshot("not-a-uuid", ACCOUNT_ID))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(400);
				assertThat(e.code()).isEqualTo("invalid_access_id");
			});
		verifyNoInteractions(this.modApiClient);
	}

	/** 连接失败 / 超时 → 503 mod_unreachable。 */
	@Test
	void connectionFailureMapsToModUnreachable() {
		when(this.modApiClient.listGrids()).thenThrow(new ResourceAccessException("connect timed out"));

		assertThatThrownBy(() -> this.service.listGrids(ACCOUNT_ID))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(503);
				assertThat(e.code()).isEqualTo("mod_unreachable");
			});
	}

	/** 模组 401（共享密钥不匹配）→ 503 mod_unavailable。 */
	@Test
	void unauthorizedMapsToModUnavailable() {
		when(this.modApiClient.listGrids()).thenThrow(new ModApiException(401, "unauthorized"));

		assertThatThrownBy(() -> this.service.listGrids(ACCOUNT_ID))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(503);
				assertThat(e.code()).isEqualTo("mod_unavailable");
			});
	}

	/** 模组其余错误码原样透传（状态码 + 错误码都不变）。 */
	@Test
	void otherModErrorsPassThrough() {
		when(this.modApiClient.getSnapshot(any(UUID.class))).thenThrow(new ModApiException(404, "snapshot_not_found"));
		when(this.modApiClient.listGrids())
			.thenReturn(new ModGridListResponse(List.of(modSummary(false, false))));

		assertThatThrownBy(() -> this.service.getSnapshot(ACCESS_ID, ACCOUNT_ID))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(404);
				assertThat(e.code()).isEqualTo("snapshot_not_found");
			});
	}

	/** 旧版模组（列表无 hasPassword 字段）→ 反序列化 null 按公开处理，unlocked=true。 */
	@Test
	void legacyModWithoutPasswordFieldTreatsGridsAsPublic() {
		// 旧版模组响应无 hasPassword 字段 → Boolean 组件为 null → service 归一为公开
		when(this.modApiClient.listGrids())
			.thenReturn(new ModGridListResponse(List.of(modSummary(false, null))));

		GridListResponse response = this.service.listGrids(ACCOUNT_ID);

		GridSummaryResponse summary = response.grids().get(0);
		assertThat(summary.hasPassword()).isFalse();
		assertThat(summary.unlocked()).isTrue();
		// 模组 lastSeenAt 原样透传到 Web 摘要（契约 v0.10）
		assertThat(summary.lastSeenAt()).isEqualTo(1759990000000L);
	}

	/** 设了密码且账号未解锁 → 列表 unlocked=false；snapshot 前置检查 → 403 grid_locked。 */
	@Test
	void lockedGridBlocksSnapshotWithGridLocked() {
		when(this.modApiClient.listGrids())
			.thenReturn(new ModGridListResponse(List.of(modSummary(true, true))))
			.thenReturn(new ModGridListResponse(List.of(modSummary(true, true))));
		when(this.gridAccessRepository.existsByAccountIdAndAccessId(ACCOUNT_ID, ACCESS_ID)).thenReturn(false);

		GridListResponse response = this.service.listGrids(ACCOUNT_ID);
		assertThat(response.grids().get(0).hasPassword()).isTrue();
		assertThat(response.grids().get(0).unlocked()).isFalse();

		assertThatThrownBy(() -> this.service.getSnapshot(ACCESS_ID, ACCOUNT_ID))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(403);
				assertThat(e.code()).isEqualTo("grid_locked");
			});
		verify(this.modApiClient, never()).getSnapshot(any(UUID.class));
	}

	/** 设了密码但账号已解锁 → unlocked=true，snapshot 正常转发模组。 */
	@Test
	void unlockedAccountPassesSnapshotThrough() {
		when(this.modApiClient.listGrids()).thenReturn(new ModGridListResponse(List.of(modSummary(true, true))));
		when(this.gridAccessRepository.existsByAccountIdAndAccessId(ACCOUNT_ID, ACCESS_ID)).thenReturn(true);

		GridListResponse response = this.service.listGrids(ACCOUNT_ID);
		assertThat(response.grids().get(0).unlocked()).isTrue();

		this.service.getSnapshot(ACCESS_ID, ACCOUNT_ID);
		verify(this.modApiClient).getSnapshot(UUID.fromString(ACCESS_ID));
	}

	/** 网格不在模组列表中（未知 UUID）→ 访问控制放行，404 语义留给模组端点。 */
	@Test
	void unknownGridBypassesLockCheck() {
		when(this.modApiClient.listGrids()).thenReturn(new ModGridListResponse(List.of()));
		when(this.modApiClient.getSnapshot(any(UUID.class))).thenThrow(new ModApiException(404, "snapshot_not_found"));

		assertThatThrownBy(() -> this.service.getSnapshot(ACCESS_ID, ACCOUNT_ID))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(404);
				assertThat(e.code()).isEqualTo("snapshot_not_found");
			});
	}

	/** 后端观察到密码被清除（hasPassword=false）→ 删除该网格全部解锁记录（撤销语义）。 */
	@Test
	void clearedPasswordRevokesUnlockRecords() {
		when(this.modApiClient.listGrids()).thenReturn(new ModGridListResponse(List.of(modSummary(false, false))));

		this.service.listGrids(ACCOUNT_ID);

		verify(this.gridAccessRepository).deleteByAccessId(ACCESS_ID);
	}

	/** unlock：模组校验通过（204）→ 按账号写入解锁记录（幂等：已有记录不重复写）。 */
	@Test
	void unlockStoresAccessRecordAfterModAccepts() {
		when(this.gridAccessRepository.existsByAccountIdAndAccessId(ACCOUNT_ID, ACCESS_ID)).thenReturn(false);

		this.service.unlock(ACCESS_ID, ACCOUNT_ID, "secret");

		verify(this.modApiClient).verifyPassword(UUID.fromString(ACCESS_ID), "secret");
		verify(this.gridAccessRepository).save(any(GridAccess.class));
	}

	/** unlock：模组 401 invalid_password → 原样透传且不写解锁记录。 */
	@Test
	void unlockWithWrongPasswordPassesThroughAndSkipsRecord() {
		doThrow(new ModApiException(401, "invalid_password"))
			.when(this.modApiClient).verifyPassword(any(UUID.class), anyString());

		assertThatThrownBy(() -> this.service.unlock(ACCESS_ID, ACCOUNT_ID, "wrong"))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(401);
				assertThat(e.code()).isEqualTo("invalid_password");
			});
		verifyNoInteractions(this.gridAccessRepository);
	}

	/** 删除命令：commandId 非法 → 400 invalid_command_id，且不触达模组客户端（契约 v0.9）。 */
	@Test
	void deleteCommandWithInvalidCommandIdMapsTo400AndSkipsModCall() {
		assertThatThrownBy(() -> this.service.deleteCommand(ACCESS_ID, "not-a-uuid", ACCOUNT_ID))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(400);
				assertThat(e.code()).isEqualTo("invalid_command_id");
			});
		verifyNoInteractions(this.modApiClient);
	}

	/** 删除命令：锁定且未解锁网格 → 403 grid_locked，不转发模组。 */
	@Test
	void deleteCommandBlockedByGridLock() {
		when(this.modApiClient.listGrids()).thenReturn(new ModGridListResponse(List.of(modSummary(true, true))));
		when(this.gridAccessRepository.existsByAccountIdAndAccessId(ACCOUNT_ID, ACCESS_ID)).thenReturn(false);

		assertThatThrownBy(() -> this.service.deleteCommand(ACCESS_ID, COMMAND_ID, ACCOUNT_ID))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(403);
				assertThat(e.code()).isEqualTo("grid_locked");
			});
		verify(this.modApiClient, never()).deleteCommand(any(UUID.class), any(UUID.class));
	}

	/** 删除命令：已解锁 → 转发模组（UUID 解析正确），模组 404 command_not_found 原样透传。 */
	@Test
	void deleteCommandPassesThroughAfterLockCheck() {
		when(this.modApiClient.listGrids()).thenReturn(new ModGridListResponse(List.of(modSummary(true, true))));
		when(this.gridAccessRepository.existsByAccountIdAndAccessId(ACCOUNT_ID, ACCESS_ID)).thenReturn(true);
		doThrow(new ModApiException(404, "command_not_found"))
			.when(this.modApiClient).deleteCommand(any(UUID.class), any(UUID.class));

		assertThatThrownBy(() -> this.service.deleteCommand(ACCESS_ID, COMMAND_ID, ACCOUNT_ID))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(404);
				assertThat(e.code()).isEqualTo("command_not_found");
			});
		verify(this.modApiClient).deleteCommand(UUID.fromString(ACCESS_ID), UUID.fromString(COMMAND_ID));
	}

	/**
	 * 构造模组侧网格摘要（{@code ModGridSummaryResponse}）。
	 *
	 * @param hasPassword 模组侧可空密码标记（null = 旧版模组缺字段）
	 */
	private static ModGridSummaryResponse modSummary(boolean locked, Boolean hasPassword) {
		return new ModGridSummaryResponse(ACCESS_ID, "主基地 ME 网格", "minecraft:overworld",
				128, 64, -256, true, 1760000000000L, 17, 2, hasPassword, 1759990000000L);
	}
}
