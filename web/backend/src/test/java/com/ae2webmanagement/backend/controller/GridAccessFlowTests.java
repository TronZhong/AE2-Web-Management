package com.ae2webmanagement.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.ae2webmanagement.backend.client.ModApiClient;
import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.domain.Account;
import com.ae2webmanagement.backend.dto.ModGridListResponse;
import com.ae2webmanagement.backend.dto.ModGridSummaryResponse;
import com.ae2webmanagement.backend.dto.SnapshotResponse;
import com.ae2webmanagement.backend.repository.AccountRepository;
import com.ae2webmanagement.backend.service.PasswordHashes;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 网格密码全链路集成测试（契约 v0.3「网格访问控制」+ v0.11 owner 免密）：真实
 * Sa-Token 拦截器 + 真实 {@code GridService} + 内存 H2（真 {@code account_grid_access}
 * 表），仅把 {@link ModApiClient} 换成 mock 模组。覆盖「锁定网格 unlocked=false →
 * snapshot 403 → 错密码 401 → 正确密码 204 → unlocked=true → snapshot 放行」
 * 与「owner 免密」的账号维度闭环。
 *
 * <p>roadmap §11 起账号只能游戏内注册——测试直接向账号表播种与模组同步产物
 * 同形的账号（玩家名 + PBKDF2 哈希 + 绑定 playerUuid）。</p>
 *
 * <p>每个用例使用独立的 accessId 对，避免类内共享 H2 中解锁记录互相干扰。</p>
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:gridaccess;DB_CLOSE_DELAY=-1",
})
@AutoConfigureMockMvc
class GridAccessFlowTests {

	private static final String PLAYER_UUID = "9b2ad6c1-4b7a-4c1e-9a3f-2f8f6f41a099";

	private static final String OTHER_PLAYER_UUID = "1a2ad6c1-4b7a-4c1e-9a3f-2f8f6f41a088";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ModApiClient modApiClient;

	@Autowired
	private AccountRepository accountRepository;

	/** 播种两个与模组同步产物同形的账号：Steve（主测账号）与 Alex（他人）。 */
	@BeforeEach
	void seedAccounts() {
		this.accountRepository.deleteAll();
		saveAccount("Steve", PLAYER_UUID);
		saveAccount("Alex", OTHER_PLAYER_UUID);
	}

	private void saveAccount(String name, String playerUuid) {
		Account account = new Account();
		account.setUsername(name);
		account.setPlayerUuid(playerUuid);
		account.setPasswordHash(PasswordHashes.encodeForTests("test-password", 60_000));
		account.setCreatedAt(Instant.now());
		this.accountRepository.save(account);
	}

	/** 便捷构造：以 Steve 身份登录并返回 satoken Cookie。 */
	private Cookie login() throws Exception {
		MvcResult login = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"Steve\",\"password\":\"test-password\"}"))
			.andExpect(status().isOk())
			.andReturn();
		return login.getResponse().getCookie("satoken");
	}

	/**
	 * 便捷构造：模组列表响应（锁定网格 + 公开网格各一，模组侧 DTO）。
	 *
	 * @param lockedId    设有密码的网格 ID
	 * @param publicId    未设密码的网格 ID
	 * @param hasPassword 锁定网格是否带密码（false 用于模拟清除密码）
	 * @param ownerUuid   锁定网格的 owner（null = 无主网格）
	 */
	private static ModGridListResponse modList(String lockedId, String publicId, boolean hasPassword, String ownerUuid) {
		return new ModGridListResponse(List.of(
				new ModGridSummaryResponse(lockedId, "锁定网格", "minecraft:overworld",
						1, 2, 3, true, 1760000000000L, 5, 1, hasPassword, 1760000000000L, ownerUuid,
						ownerUuid == null ? null : "Alex"),
				new ModGridSummaryResponse(publicId, "公开网格", "minecraft:overworld",
						4, 5, 6, false, 0, 0, 0, false, 1759990000000L, PLAYER_UUID, "Steve")));
	}

	/** 闭环：锁定网格 unlocked=false → snapshot 403 → 错密码 401 → 正确密码 204 → unlocked=true → snapshot 200。 */
	@Test
	void unlockFlowPersistsPerAccountAndGrantsAccess() throws Exception {
		String lockedId = UUID.randomUUID().toString();
		String publicId = UUID.randomUUID().toString();
		UUID lockedUuid = UUID.fromString(lockedId);
		when(this.modApiClient.listGrids()).thenReturn(modList(lockedId, publicId, true, OTHER_PLAYER_UUID));
		doNothing().when(this.modApiClient).verifyPassword(eq(lockedUuid), eq("secret"));
		when(this.modApiClient.getSnapshot(eq(lockedUuid)))
			.thenReturn(new SnapshotResponse(lockedId, 1760000000000L, true, List.of(), List.of(), List.of()));

		Cookie satoken = login();

		// 列表可见但显示锁定；公开网格 unlocked 恒 true；owner 字段透传（契约 v0.11）
		this.mockMvc.perform(get("/api/grids").cookie(satoken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grids[0].hasPassword").value(true))
			.andExpect(jsonPath("$.grids[0].unlocked").value(false))
			.andExpect(jsonPath("$.grids[0].ownerUuid").value(OTHER_PLAYER_UUID))
			.andExpect(jsonPath("$.grids[0].ownerName").value("Alex"))
			.andExpect(jsonPath("$.grids[1].hasPassword").value(false))
			.andExpect(jsonPath("$.grids[1].unlocked").value(true))
			.andExpect(jsonPath("$.grids[1].ownerName").value("Steve"));

		// 未解锁的锁定网格 snapshot → 403 grid_locked
		this.mockMvc.perform(get("/api/grids/" + lockedId + "/snapshot").cookie(satoken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("grid_locked"));

		// 错密码 → 401 invalid_password
		doThrow(new ModApiException(401, "invalid_password"))
			.when(this.modApiClient).verifyPassword(eq(lockedUuid), eq("wrong"));
		this.mockMvc.perform(post("/api/grids/" + lockedId + "/unlock")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"wrong\"}")
				.cookie(satoken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("invalid_password"));

		// 正确密码 → 204，解锁按账号持久记忆
		this.mockMvc.perform(post("/api/grids/" + lockedId + "/unlock")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"secret\"}")
				.cookie(satoken))
			.andExpect(status().isNoContent());

		this.mockMvc.perform(get("/api/grids").cookie(satoken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grids[0].unlocked").value(true));

		this.mockMvc.perform(get("/api/grids/" + lockedId + "/snapshot").cookie(satoken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessId").value(lockedId));
	}

	/** owner 免密（契约 v0.11，roadmap §11）：owner 账号不解锁也 unlocked=true，snapshot 直接放行。 */
	@Test
	void ownerBypassesGridLock() throws Exception {
		String lockedId = UUID.randomUUID().toString();
		String publicId = UUID.randomUUID().toString();
		when(this.modApiClient.listGrids()).thenReturn(modList(lockedId, publicId, true, PLAYER_UUID));
		when(this.modApiClient.getSnapshot(eq(UUID.fromString(lockedId))))
			.thenReturn(new SnapshotResponse(lockedId, 1760000000000L, true, List.of(), List.of(), List.of()));

		Cookie satoken = login();

		this.mockMvc.perform(get("/api/grids").cookie(satoken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grids[0].hasPassword").value(true))
			.andExpect(jsonPath("$.grids[0].unlocked").value(true));

		this.mockMvc.perform(get("/api/grids/" + lockedId + "/snapshot").cookie(satoken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessId").value(lockedId));
	}

	/** 非 owner 且无解锁记录的账号仍被锁定网格拦截（owner 免密不外溢）。 */
	@Test
	void nonOwnerStillLockedWithoutUnlockRecord() throws Exception {
		String lockedId = UUID.randomUUID().toString();
		String publicId = UUID.randomUUID().toString();
		// owner 是第三方 UUID（既非 Steve 也非 Alex）
		when(this.modApiClient.listGrids()).thenReturn(modList(lockedId, publicId, true, "00000000-0000-0000-0000-00000000000f"));

		Cookie satoken = login();

		this.mockMvc.perform(get("/api/grids").cookie(satoken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grids[0].unlocked").value(false));

		this.mockMvc.perform(get("/api/grids/" + lockedId + "/snapshot").cookie(satoken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("grid_locked"));
	}

	/** 解锁幂等：重复 unlock 仍 204 且不影响后续访问。 */
	@Test
	void repeatedUnlockStaysIdempotent() throws Exception {
		String lockedId = UUID.randomUUID().toString();
		String publicId = UUID.randomUUID().toString();
		UUID lockedUuid = UUID.fromString(lockedId);
		when(this.modApiClient.listGrids()).thenReturn(modList(lockedId, publicId, true, OTHER_PLAYER_UUID));
		doNothing().when(this.modApiClient).verifyPassword(eq(lockedUuid), eq("secret"));

		Cookie satoken = login();

		this.mockMvc.perform(post("/api/grids/" + lockedId + "/unlock")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"secret\"}")
				.cookie(satoken))
			.andExpect(status().isNoContent());

		this.mockMvc.perform(post("/api/grids/" + lockedId + "/unlock")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"secret\"}")
				.cookie(satoken))
			.andExpect(status().isNoContent());

		this.mockMvc.perform(get("/api/grids").cookie(satoken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grids[0].unlocked").value(true));
	}

	/** 清除密码撤销语义：模组观察到 hasPassword=false → 网格回到公开（unlocked 恒 true）。 */
	@Test
	void clearedPasswordMakesGridPublicAgain() throws Exception {
		String lockedId = UUID.randomUUID().toString();
		String publicId = UUID.randomUUID().toString();
		when(this.modApiClient.listGrids()).thenReturn(modList(lockedId, publicId, false, OTHER_PLAYER_UUID));
		Cookie satoken = login();

		this.mockMvc.perform(get("/api/grids").cookie(satoken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grids[0].hasPassword").value(false))
			.andExpect(jsonPath("$.grids[0].unlocked").value(true));
	}
}
