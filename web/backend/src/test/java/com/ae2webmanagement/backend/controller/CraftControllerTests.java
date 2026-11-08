package com.ae2webmanagement.backend.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.dto.CraftAcceptedResponse;
import com.ae2webmanagement.backend.dto.CraftRequest;
import com.ae2webmanagement.backend.dto.CommandEntryResponse;
import com.ae2webmanagement.backend.dto.CommandListResponse;
import com.ae2webmanagement.backend.service.AuthService;
import com.ae2webmanagement.backend.service.GridService;

import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link CraftController} MockMvc 测试：合成命令端点的字段对齐、
 * 请求体校验（与模组行为一致）与 202 受理语义。
 *
 * <p>切片不加载 Sa-Token 自动配置，{@code app.auth.enabled=false} 关闭登录拦截，
 * 认证链路由 {@link AuthFlowTests} 全上下文覆盖；网格访问控制（v0.3）在
 * {@link GridService} 内实现，由其单元测试覆盖，本切片只断言转发形状。</p>
 */
@WebMvcTest(value = CraftController.class, properties = "app.auth.enabled=false")
class CraftControllerTests {

	private static final String ACCESS_ID = "0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001";

	private static final long ACCOUNT_ID = 42L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GridService gridService;

	@MockitoBean
	private AuthService authService;

	/** controller 转发前都会取当前账号 ID；统一固定 stub（校验失败用例不会走到，用 lenient）。 */
	@BeforeEach
	void stubCurrentAccount() {
		lenient().when(this.authService.currentAccountId()).thenReturn(ACCOUNT_ID);
	}

	/** POST /craft 正常路径：返回 202 且 commandId/state/submittedAt 与契约对齐。 */
	@Test
	void craftReturns202WithAcceptedFields() throws Exception {
		when(this.gridService.submitCraft(eq(ACCESS_ID), any(CraftRequest.class), eq(ACCOUNT_ID)))
			.thenReturn(new CraftAcceptedResponse("5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100", "PENDING", 1760000000000L));

		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/craft")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"key\":{\"id\":\"minecraft:stone\"},\"amount\":64}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.commandId").value("5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100"))
			.andExpect(jsonPath("$.state").value("PENDING"))
			.andExpect(jsonPath("$.submittedAt").value(1760000000000L));
	}

	/** 校验对齐：缺失 key 或 amount → 400 missing_key_or_amount（模拟模组行为）。 */
	@Test
	void craftWithMissingKeyOrAmountReturns400() throws Exception {
		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/craft")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":64}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("missing_key_or_amount"));
	}

	/** 校验对齐：amount ≤ 0 → 400 invalid_amount。 */
	@Test
	void craftWithNonPositiveAmountReturns400() throws Exception {
		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/craft")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"key\":{\"id\":\"minecraft:stone\"},\"amount\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_amount"));
	}

	/** 校验对齐：key 为空 object → 400 invalid_key。 */
	@Test
	void craftWithEmptyKeyObjectReturns400() throws Exception {
		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/craft")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"key\":{},\"amount\":64}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_key"));
	}

	/** 校验对齐：key 不是 object → 400 invalid_key。 */
	@Test
	void craftWithNonObjectKeyReturns400() throws Exception {
		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/craft")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"key\":\"minecraft:stone\",\"amount\":64}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_key"));
	}

	/** 校验对齐：请求体不是 JSON → 400 invalid_json（对齐模组 invalid_json）。 */
	@Test
	void craftWithMalformedJsonReturns400() throws Exception {
		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/craft")
				.contentType(MediaType.APPLICATION_JSON)
				.content("not-json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_json"));
	}

	/** GET /commands：命令条目字段名与契约逐一断言（target 为 JsonNode 透传）。 */
	@Test
	void commandsFieldNamesMatchContract() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		when(this.gridService.getCommands(ACCESS_ID, ACCOUNT_ID)).thenReturn(new CommandListResponse(List.of(
				new CommandEntryResponse("5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100", ACCESS_ID, "SUBMITTED",
						1760000000000L, "crafting-1", 64, mapper.readTree("{\"id\":\"minecraft:stone\"}")))));

		this.mockMvc.perform(get("/api/grids/" + ACCESS_ID + "/commands"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.commands[0].commandId").value("5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100"))
			.andExpect(jsonPath("$.commands[0].accessId").value(ACCESS_ID))
			.andExpect(jsonPath("$.commands[0].state").value("SUBMITTED"))
			.andExpect(jsonPath("$.commands[0].submittedAt").value(1760000000000L))
			.andExpect(jsonPath("$.commands[0].result").value("crafting-1"))
			.andExpect(jsonPath("$.commands[0].amount").value(64))
			.andExpect(jsonPath("$.commands[0].target.id").value("minecraft:stone"));
	}

	/** 错误透传：模组 grid_not_found → 404 {"error":"grid_not_found"}。 */
	@Test
	void modErrorIsRenderedAsContractErrorBody() throws Exception {
		when(this.gridService.submitCraft(eq(ACCESS_ID), any(CraftRequest.class), eq(ACCOUNT_ID)))
			.thenThrow(new ModApiException(404, "grid_not_found"));

		this.mockMvc.perform(post("/api/grids/" + ACCESS_ID + "/craft")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"key\":{\"id\":\"minecraft:stone\"},\"amount\":64}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("grid_not_found"));
	}
}
