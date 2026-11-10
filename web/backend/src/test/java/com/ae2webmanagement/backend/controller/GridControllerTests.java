package com.ae2webmanagement.backend.controller;

import java.util.List;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.dto.CpuEntry;
import com.ae2webmanagement.backend.dto.GridListResponse;
import com.ae2webmanagement.backend.dto.GridSummaryResponse;
import com.ae2webmanagement.backend.dto.PatternEntry;
import com.ae2webmanagement.backend.dto.PatternInputEntry;
import com.ae2webmanagement.backend.dto.SnapshotResponse;
import com.ae2webmanagement.backend.dto.StackEntry;
import com.ae2webmanagement.backend.service.AuthService;
import com.ae2webmanagement.backend.service.GridService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GridController} MockMvc 测试：断言响应 JSON 字段名与
 * docs/contracts/web-api.md ≡ 前端 fixtures.js 逐一对齐，以及错误码经
 * GlobalExceptionHandler 的输出形状；v0.3 另覆盖解锁端点与
 * {@code hasPassword}/{@code unlocked} 字段、grid_locked 错误形状。
 *
 * <p>切片不加载 Sa-Token 自动配置，{@code app.auth.enabled=false} 关闭登录拦截，
 * 认证链路由 {@link AuthFlowTests} 全上下文覆盖；accountId 由 mock 的
 * {@link AuthService} 提供。</p>
 */
@WebMvcTest(value = GridController.class, properties = "app.auth.enabled=false")
class GridControllerTests {

	private static final String ACCESS_ID = "0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GridService gridService;

	@MockitoBean
	private AuthService authService;

	/** 切片内所有端点都会先取当前账号 ID，统一固定 stub（部分校验失败用例不会走到，用 lenient 避免 UnnecessaryStubbing）。 */
	@BeforeEach
	void stubCurrentAccount() {
		org.mockito.Mockito.lenient().when(this.authService.currentAccountId()).thenReturn(42L);
	}

	/** GET /api/grids：网格列表字段名与契约逐一断言（含 v0.3 hasPassword/unlocked）。 */
	@Test
	void gridsFieldNamesMatchContract() throws Exception {
		when(this.gridService.listGrids(42L)).thenReturn(new GridListResponse(List.of(
				new GridSummaryResponse(ACCESS_ID, "主基地 ME 网格", "minecraft:overworld",
						128, 64, -256, true, 1760000000000L, 17, 2, true, false, 1759990000000L))));

		this.mockMvc.perform(get("/api/grids"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grids[0].accessId").value(ACCESS_ID))
			.andExpect(jsonPath("$.grids[0].displayName").value("主基地 ME 网格"))
			.andExpect(jsonPath("$.grids[0].dimension").value("minecraft:overworld"))
			.andExpect(jsonPath("$.grids[0].controllerX").value(128))
			.andExpect(jsonPath("$.grids[0].controllerY").value(64))
			.andExpect(jsonPath("$.grids[0].controllerZ").value(-256))
			.andExpect(jsonPath("$.grids[0].online").value(true))
			.andExpect(jsonPath("$.grids[0].capturedAt").value(1760000000000L))
			.andExpect(jsonPath("$.grids[0].inventoryEntries").value(17))
			.andExpect(jsonPath("$.grids[0].cpuCount").value(2))
			.andExpect(jsonPath("$.grids[0].hasPassword").value(true))
			.andExpect(jsonPath("$.grids[0].unlocked").value(false));
	}

	/** GET /api/grids/{accessId}/snapshot：快照嵌套字段名与契约逐一断言（含可选 nameKey，roadmap.md §4）。 */
	@Test
	void snapshotFieldNamesMatchContract() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		when(this.gridService.getSnapshot(ACCESS_ID, 42L)).thenReturn(new SnapshotResponse(
				ACCESS_ID, 1760000000000L, true,
				List.of(new StackEntry(mapper.readTree("{\"id\":\"minecraft:stone\"}"), 12844, "block.minecraft.stone")),
				List.of(new CpuEntry(false, 16384, 0)),
				List.of(new PatternEntry(
						new StackEntry(mapper.readTree("{\"id\":\"minecraft:stick\"}"), 4, "item.minecraft.stick"),
						// 无 nameKey 的条目：模拟旧版模组快照，验证 Web 响应省略该字段
						List.of(new StackEntry(mapper.readTree("{\"id\":\"minecraft:stick\"}"), 4, null)),
						List.of(new PatternInputEntry(
								List.of(new StackEntry(mapper.readTree("{\"id\":\"minecraft:oak_planks\"}"), 2, "block.minecraft.oak_planks")), 1))))));

		this.mockMvc.perform(get("/api/grids/" + ACCESS_ID + "/snapshot"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessId").value(ACCESS_ID))
			.andExpect(jsonPath("$.capturedAt").value(1760000000000L))
			.andExpect(jsonPath("$.online").value(true))
			.andExpect(jsonPath("$.inventory[0].key.id").value("minecraft:stone"))
			.andExpect(jsonPath("$.inventory[0].amount").value(12844))
			.andExpect(jsonPath("$.inventory[0].nameKey").value("block.minecraft.stone"))
			// 契约 v0.8：craftingCpus[] 无 name 字段（CPU 无法命名，前端按序号 #N 显示）
			.andExpect(jsonPath("$.craftingCpus[0].name").doesNotExist())
			.andExpect(jsonPath("$.craftingCpus[0].busy").value(false))
			.andExpect(jsonPath("$.craftingCpus[0].availableStorage").value(16384))
			.andExpect(jsonPath("$.craftingCpus[0].coProcessors").value(0))
			.andExpect(jsonPath("$.patterns[0].primaryOutput.key.id").value("minecraft:stick"))
			.andExpect(jsonPath("$.patterns[0].primaryOutput.amount").value(4))
			.andExpect(jsonPath("$.patterns[0].primaryOutput.nameKey").value("item.minecraft.stick"))
			.andExpect(jsonPath("$.patterns[0].outputs[0].key.id").value("minecraft:stick"))
			// 旧版模组无 nameKey → Web 响应省略该字段（向后兼容）
			.andExpect(jsonPath("$.patterns[0].outputs[0].nameKey").doesNotExist())
			.andExpect(jsonPath("$.patterns[0].inputs[0].possibleInputs[0].key.id").value("minecraft:oak_planks"))
			.andExpect(jsonPath("$.patterns[0].inputs[0].possibleInputs[0].nameKey").value("block.minecraft.oak_planks"))
			.andExpect(jsonPath("$.patterns[0].inputs[0].multiplier").value(1));
	}

	/** 错误翻译：service 抛 snapshot_not_found → 404 {"error":"snapshot_not_found"}。 */
	@Test
	void modErrorIsRenderedAsContractErrorBody() throws Exception {
		when(this.gridService.getSnapshot(ACCESS_ID, 42L)).thenThrow(new ModApiException(404, "snapshot_not_found"));

		this.mockMvc.perform(get("/api/grids/" + ACCESS_ID + "/snapshot"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("snapshot_not_found"));
	}

	/** 错误翻译：service 抛 grid_locked → 403 {"error":"grid_locked"}（v0.3 锁定网格）。 */
	@Test
	void gridLockedIsRenderedAsContractErrorBody() throws Exception {
		when(this.gridService.getSnapshot(ACCESS_ID, 42L)).thenThrow(new ModApiException(403, "grid_locked"));

		this.mockMvc.perform(get("/api/grids/" + ACCESS_ID + "/snapshot"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("grid_locked"));
	}

	/** 错误翻译：service 抛 mod_unreachable → 503 {"error":"mod_unreachable"}（游戏未启动场景）。 */
	@Test
	void modUnreachableIsRenderedAsContractErrorBody() throws Exception {
		when(this.gridService.listGrids(42L)).thenThrow(new ModApiException(503, "mod_unreachable"));

		this.mockMvc.perform(get("/api/grids"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.error").value("mod_unreachable"));
	}

	/** 错误翻译：service 抛 invalid_access_id → 400 {"error":"invalid_access_id"}。 */
	@Test
	void invalidAccessIdIsRenderedAsContractErrorBody() throws Exception {
		when(this.gridService.getSnapshot("not-a-uuid", 42L)).thenThrow(new ModApiException(400, "invalid_access_id"));

		this.mockMvc.perform(get("/api/grids/not-a-uuid/snapshot"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_access_id"));
	}

	/** POST /unlock 正常路径：204 无响应体（契约 v0.3）。 */
	@Test
	void unlockReturns204WithoutBody() throws Exception {
		org.mockito.Mockito.doNothing()
			.when(this.gridService).unlock(ACCESS_ID, 42L, "secret");

		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/unlock")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"secret\"}"))
			.andExpect(status().isNoContent());
	}

	/** 校验对齐：缺失/空白 password → 400 {"error":"invalid_json"}（不触达 service）。 */
	@Test
	void unlockWithMissingPasswordIsInvalidJson() throws Exception {
		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/unlock")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_json"));
	}

	/** 校验对齐：请求体不是 JSON → 400 {"error":"invalid_json"}。 */
	@Test
	void unlockWithMalformedBodyIsInvalidJson() throws Exception {
		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/unlock")
				.contentType(MediaType.APPLICATION_JSON)
				.content("not-json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_json"));
	}

	/** 错误透传：密码不匹配 → 401 {"error":"invalid_password"}。 */
	@Test
	void unlockWithWrongPasswordRendersInvalidPassword() throws Exception {
		org.mockito.Mockito.doThrow(new ModApiException(401, "invalid_password"))
			.when(this.gridService).unlock(ACCESS_ID, 42L, "wrong");

		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/unlock")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"wrong\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("invalid_password"));
	}

	/** 错误透传：网格不存在 → 404 {"error":"grid_not_found"}。 */
	@Test
	void unlockWithUnknownGridRendersGridNotFound() throws Exception {
		org.mockito.Mockito.doThrow(new ModApiException(404, "grid_not_found"))
			.when(this.gridService).unlock(ACCESS_ID, 42L, "secret");

		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/unlock")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"secret\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("grid_not_found"));
	}
}
