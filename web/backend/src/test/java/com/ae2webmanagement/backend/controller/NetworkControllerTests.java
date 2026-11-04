package com.ae2webmanagement.backend.controller;

import java.util.List;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.dto.CpuEntry;
import com.ae2webmanagement.backend.dto.NetworkListResponse;
import com.ae2webmanagement.backend.dto.NetworkSummaryResponse;
import com.ae2webmanagement.backend.dto.PatternEntry;
import com.ae2webmanagement.backend.dto.PatternInputEntry;
import com.ae2webmanagement.backend.dto.SnapshotResponse;
import com.ae2webmanagement.backend.dto.StackEntry;
import com.ae2webmanagement.backend.service.NetworkService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link NetworkController} MockMvc 测试：断言响应 JSON 字段名与
 * docs/contracts/web-api.md ≡ 前端 fixtures.js 逐一对齐，以及错误码经
 * GlobalExceptionHandler 的输出形状。
 */
@WebMvcTest(NetworkController.class)
class NetworkControllerTests {

	private static final String ACCESS_ID = "0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NetworkService networkService;

	/** GET /api/networks：网络列表字段名与契约逐一断言。 */
	@Test
	void networksFieldNamesMatchContract() throws Exception {
		when(this.networkService.listNetworks()).thenReturn(new NetworkListResponse(List.of(
				new NetworkSummaryResponse(ACCESS_ID, "主基地 ME 网络", "minecraft:overworld",
						128, 64, -256, true, 1760000000000L, 17, 2))));

		this.mockMvc.perform(get("/api/networks"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.networks[0].accessId").value(ACCESS_ID))
			.andExpect(jsonPath("$.networks[0].displayName").value("主基地 ME 网络"))
			.andExpect(jsonPath("$.networks[0].dimension").value("minecraft:overworld"))
			.andExpect(jsonPath("$.networks[0].controllerX").value(128))
			.andExpect(jsonPath("$.networks[0].controllerY").value(64))
			.andExpect(jsonPath("$.networks[0].controllerZ").value(-256))
			.andExpect(jsonPath("$.networks[0].online").value(true))
			.andExpect(jsonPath("$.networks[0].capturedAt").value(1760000000000L))
			.andExpect(jsonPath("$.networks[0].inventoryEntries").value(17))
			.andExpect(jsonPath("$.networks[0].cpuCount").value(2));
	}

	/** GET /api/networks/{accessId}/snapshot：快照嵌套字段名与契约逐一断言。 */
	@Test
	void snapshotFieldNamesMatchContract() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		when(this.networkService.getSnapshot(ACCESS_ID)).thenReturn(new SnapshotResponse(
				ACCESS_ID, 1760000000000L, true,
				List.of(new StackEntry(mapper.readTree("{\"id\":\"minecraft:stone\"}"), 12844)),
				List.of(new CpuEntry("", false, 16384, 0)),
				List.of(new PatternEntry(
						new StackEntry(mapper.readTree("{\"id\":\"minecraft:stick\"}"), 4),
						List.of(new StackEntry(mapper.readTree("{\"id\":\"minecraft:stick\"}"), 4)),
						List.of(new PatternInputEntry(
								List.of(new StackEntry(mapper.readTree("{\"id\":\"minecraft:oak_planks\"}"), 2)), 1))))));

		this.mockMvc.perform(get("/api/networks/" + ACCESS_ID + "/snapshot"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessId").value(ACCESS_ID))
			.andExpect(jsonPath("$.capturedAt").value(1760000000000L))
			.andExpect(jsonPath("$.online").value(true))
			.andExpect(jsonPath("$.inventory[0].key.id").value("minecraft:stone"))
			.andExpect(jsonPath("$.inventory[0].amount").value(12844))
			.andExpect(jsonPath("$.craftingCpus[0].name").value(""))
			.andExpect(jsonPath("$.craftingCpus[0].busy").value(false))
			.andExpect(jsonPath("$.craftingCpus[0].availableStorage").value(16384))
			.andExpect(jsonPath("$.craftingCpus[0].coProcessors").value(0))
			.andExpect(jsonPath("$.patterns[0].primaryOutput.key.id").value("minecraft:stick"))
			.andExpect(jsonPath("$.patterns[0].primaryOutput.amount").value(4))
			.andExpect(jsonPath("$.patterns[0].outputs[0].key.id").value("minecraft:stick"))
			.andExpect(jsonPath("$.patterns[0].inputs[0].possibleInputs[0].key.id").value("minecraft:oak_planks"))
			.andExpect(jsonPath("$.patterns[0].inputs[0].multiplier").value(1));
	}

	/** 错误翻译：service 抛 snapshot_not_found → 404 {"error":"snapshot_not_found"}。 */
	@Test
	void modErrorIsRenderedAsContractErrorBody() throws Exception {
		when(this.networkService.getSnapshot(ACCESS_ID)).thenThrow(new ModApiException(404, "snapshot_not_found"));

		this.mockMvc.perform(get("/api/networks/" + ACCESS_ID + "/snapshot"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("snapshot_not_found"));
	}

	/** 错误翻译：service 抛 mod_unreachable → 503 {"error":"mod_unreachable"}（游戏未启动场景）。 */
	@Test
	void modUnreachableIsRenderedAsContractErrorBody() throws Exception {
		when(this.networkService.listNetworks()).thenThrow(new ModApiException(503, "mod_unreachable"));

		this.mockMvc.perform(get("/api/networks"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.error").value("mod_unreachable"));
	}

	/** 错误翻译：service 抛 invalid_access_id → 400 {"error":"invalid_access_id"}。 */
	@Test
	void invalidAccessIdIsRenderedAsContractErrorBody() throws Exception {
		when(this.networkService.getSnapshot("not-a-uuid")).thenThrow(new ModApiException(400, "invalid_access_id"));

		this.mockMvc.perform(get("/api/networks/not-a-uuid/snapshot"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_access_id"));
	}
}
