package com.ae2webmanagement.backend.client;

import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ae2webmanagement.backend.dto.CraftRequest;
import com.ae2webmanagement.backend.dto.ModGridListResponse;
import com.ae2webmanagement.backend.dto.SnapshotResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link ModApiClient} 单元测试：用 {@link MockRestServiceServer} 绑定 RestClient，
 * 覆盖正常响应反序列化、模组错误码透传、错误体异常时的兜底。
 * 字段形状以 docs/contracts/web-api.md（≡ 前端 fixtures.js）为准。
 */
class ModApiClientTests {

	private static final String BASE_URL = "http://mod.local";

	private MockRestServiceServer server;

	private ModApiClient client;

	/** 每个用例独立构建：mock server 绑定到与生产一致的 RestClient 构造路径（baseUrl + 共享密钥头）。 */
	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl(BASE_URL)
			.defaultHeader("X-Shared-Secret", "test-secret");
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.client = new ModApiClient(builder.build(), new ObjectMapper());
	}

	/** 正常响应：GET /grids 解析为 ModGridListResponse，字段一一对应（含 v0.3 hasPassword）。 */
	@Test
	void listGridsParsesGridsArray() {
		this.server.expect(requestTo(BASE_URL + "/grids"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("X-Shared-Secret", "test-secret"))
			.andRespond(withSuccess("""
					{"grids":[{
						"accessId":"0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001",
						"displayName":"主基地 ME 网格",
						"dimension":"minecraft:overworld",
						"controllerX":128,"controllerY":64,"controllerZ":-256,
						"online":true,
						"capturedAt":1760000000000,
						"inventoryEntries":17,
						"cpuCount":2,
						"hasPassword":true,
						"lastSeenAt":1760000000001,
						"ownerUuid":"9b2ad6c1-4b7a-4c1e-9a3f-2f8f6f41a099",
						"ownerName":"Steve"
					}]}
				""", MediaType.APPLICATION_JSON));

		ModGridListResponse response = this.client.listGrids();

		assertThat(response.grids()).hasSize(1);
		assertThat(response.grids().get(0).accessId()).isEqualTo("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		assertThat(response.grids().get(0).displayName()).isEqualTo("主基地 ME 网格");
		assertThat(response.grids().get(0).dimension()).isEqualTo("minecraft:overworld");
		assertThat(response.grids().get(0).controllerX()).isEqualTo(128);
		assertThat(response.grids().get(0).controllerY()).isEqualTo(64);
		assertThat(response.grids().get(0).controllerZ()).isEqualTo(-256);
		assertThat(response.grids().get(0).online()).isTrue();
		assertThat(response.grids().get(0).capturedAt()).isEqualTo(1760000000000L);
		assertThat(response.grids().get(0).inventoryEntries()).isEqualTo(17);
		assertThat(response.grids().get(0).cpuCount()).isEqualTo(2);
		assertThat(response.grids().get(0).hasPassword()).isTrue();
		// 契约 v0.10：lastSeenAt 最近一次在线时间随摘要透传
		assertThat(response.grids().get(0).lastSeenAt()).isEqualTo(1760000000001L);
		// 契约 v0.11（模组 0.1.0）：owner 字段随摘要透传
		assertThat(response.grids().get(0).ownerUuid()).isEqualTo("9b2ad6c1-4b7a-4c1e-9a3f-2f8f6f41a099");
		assertThat(response.grids().get(0).ownerName()).isEqualTo("Steve");
		this.server.verify();
	}

	/** 契约 v0.11（模组 0.1.0）：GET /accounts 解析账号列表（含 PBKDF2 哈希与 createdAt）。 */
	@Test
	void listAccountsParsesAccountsArray() {
		this.server.expect(requestTo(BASE_URL + "/accounts"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("X-Shared-Secret", "test-secret"))
			.andRespond(withSuccess("""
					{"accounts":[{
						"playerUuid":"9b2ad6c1-4b7a-4c1e-9a3f-2f8f6f41a099",
						"playerName":"Steve",
						"passwordHash":"pbkdf2:60000:c2FsdA==:aGFzaA==",
						"createdAt":1760000000000
					}]}
				""", MediaType.APPLICATION_JSON));

		var response = this.client.listAccounts();

		assertThat(response.accounts()).hasSize(1);
		assertThat(response.accounts().get(0).playerUuid()).isEqualTo("9b2ad6c1-4b7a-4c1e-9a3f-2f8f6f41a099");
		assertThat(response.accounts().get(0).playerName()).isEqualTo("Steve");
		assertThat(response.accounts().get(0).passwordHash()).isEqualTo("pbkdf2:60000:c2FsdA==:aGFzaA==");
		assertThat(response.accounts().get(0).createdAt()).isEqualTo(1760000000000L);
		this.server.verify();
	}

	/**
	 * 向后兼容：旧版模组（&lt;0.0.6）列表无 hasPassword 字段 → 反序列化为
	 * {@code null}（service 按「无密码 = 公开」归一，无需特判）。
	 */
	@Test
	void legacyGridListWithoutHasPasswordDefaultsToNull() {
		this.server.expect(requestTo(BASE_URL + "/grids"))
			.andRespond(withSuccess("""
					{"grids":[{
						"accessId":"0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001",
						"displayName":"旧版模组网格",
						"dimension":"minecraft:overworld",
						"controllerX":1,"controllerY":2,"controllerZ":3,
						"online":false,
						"capturedAt":0,
						"inventoryEntries":0,
						"cpuCount":0
					}]}
				""", MediaType.APPLICATION_JSON));

		ModGridListResponse response = this.client.listGrids();

		assertThat(response.grids().get(0).hasPassword()).isNull();
		// 旧版模组（<0.0.14）无 lastSeenAt 字段 → null，Web 响应省略（向后兼容）
		assertThat(response.grids().get(0).lastSeenAt()).isNull();
		// 旧版模组（<0.1.0）无 owner 字段 → null，Web 响应省略（向后兼容）
		assertThat(response.grids().get(0).ownerUuid()).isNull();
		assertThat(response.grids().get(0).ownerName()).isNull();
		this.server.verify();
	}

	/** 正常响应：GET /grids/{id}/snapshot 解析嵌套结构，key 为 JsonNode 透传。 */
	@Test
	void getSnapshotParsesNestedStructure() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		this.server.expect(requestTo(BASE_URL + "/grids/" + accessId + "/snapshot"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
					{
						"accessId":"0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001",
						"capturedAt":1760000000000,
						"online":true,
						"inventory":[{"key":{"id":"minecraft:stone"},"amount":12844,"nameKey":"block.minecraft.stone"}],
						"craftingCpus":[{"busy":false,"availableStorage":16384,"coProcessors":0}],
						"patterns":[{
							"primaryOutput":{"key":{"id":"minecraft:stick"},"amount":4,"nameKey":"item.minecraft.stick"},
							"outputs":[{"key":{"id":"minecraft:stick"},"amount":4}],
							"inputs":[{"possibleInputs":[{"key":{"id":"minecraft:oak_planks"},"amount":2}],"multiplier":1}]
						}]
					}
				""", MediaType.APPLICATION_JSON));

		SnapshotResponse response = this.client.getSnapshot(accessId);

		assertThat(response.accessId()).isEqualTo(accessId.toString());
		assertThat(response.capturedAt()).isEqualTo(1760000000000L);
		assertThat(response.online()).isTrue();
		assertThat(response.inventory().get(0).key().get("id").asText()).isEqualTo("minecraft:stone");
		assertThat(response.inventory().get(0).amount()).isEqualTo(12844);
		assertThat(response.inventory().get(0).nameKey()).isEqualTo("block.minecraft.stone");
		// 契约 v0.8：craftingCpus[] 不含 name（CPU 无法命名），透传其余字段
		assertThat(response.craftingCpus().get(0).busy()).isFalse();
		assertThat(response.craftingCpus().get(0).availableStorage()).isEqualTo(16384);
		assertThat(response.patterns().get(0).primaryOutput().key().get("id").asText()).isEqualTo("minecraft:stick");
		assertThat(response.patterns().get(0).primaryOutput().nameKey()).isEqualTo("item.minecraft.stick");
		// 旧版模组快照条目缺 nameKey → 反序列化为 null（可选字段兼容）
		assertThat(response.patterns().get(0).outputs().get(0).nameKey()).isNull();
		assertThat(response.patterns().get(0).inputs().get(0).multiplier()).isEqualTo(1);
		assertThat(response.patterns().get(0).inputs().get(0).possibleInputs().get(0).amount()).isEqualTo(2);
		this.server.verify();
	}

	/** 正常响应：POST /craft 以 JSON 提交 {key, amount} 并解析 202 受理体。 */
	@Test
	void submitCraftSendsKeyAndAmountParsesAccepted() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		this.server.expect(requestTo(BASE_URL + "/grids/" + accessId + "/craft"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.key.id").value("minecraft:stone"))
			.andExpect(jsonPath("$.amount").value(64))
			.andRespond(withSuccess("""
					{"commandId":"5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100","state":"PENDING","submittedAt":1760000000000}
				""", MediaType.APPLICATION_JSON));

		var response = this.client.submitCraft(accessId, new CraftRequest(key("{\"id\":\"minecraft:stone\"}"), 64L));

		assertThat(response.commandId()).isEqualTo("5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100");
		assertThat(response.state()).isEqualTo("PENDING");
		assertThat(response.submittedAt()).isEqualTo(1760000000000L);
		this.server.verify();
	}

	/** 正常响应：GET /commands 解析命令条目，target 为 JsonNode 透传。 */
	@Test
	void getCommandsParsesCommandEntries() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		this.server.expect(requestTo(BASE_URL + "/grids/" + accessId + "/commands"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
					{"commands":[{
						"commandId":"5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100",
						"accessId":"0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001",
						"state":"SUBMITTED",
						"submittedAt":1760000000000,
						"result":"crafting-1",
						"amount":64,
						"target":{"id":"minecraft:stone"}
					}]}
				""", MediaType.APPLICATION_JSON));

		var response = this.client.getCommands(accessId);

		assertThat(response.commands()).hasSize(1);
		assertThat(response.commands().get(0).commandId()).isEqualTo("5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100");
		assertThat(response.commands().get(0).state()).isEqualTo("SUBMITTED");
		assertThat(response.commands().get(0).result()).isEqualTo("crafting-1");
		assertThat(response.commands().get(0).amount()).isEqualTo(64);
		assertThat(response.commands().get(0).target().get("id").asText()).isEqualTo("minecraft:stone");
		this.server.verify();
	}

	/** 正常响应：POST /verify-password 密码正确 → 204 无响应体（v0.3）。 */
	@Test
	void verifyPasswordSendsPasswordAndAccepts204() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		this.server.expect(requestTo(BASE_URL + "/grids/" + accessId + "/verify-password"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.password").value("secret"))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		this.client.verifyPassword(accessId, "secret");

		this.server.verify();
	}

	/** 错误透传：POST /verify-password 密码不匹配 → ModApiException(401, invalid_password)（v0.3）。 */
	@Test
	void verifyPasswordWrongPasswordPassesThrough401() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		this.server.expect(requestTo(BASE_URL + "/grids/" + accessId + "/verify-password"))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"invalid_password\"}"));

		assertThatThrownBy(() -> this.client.verifyPassword(accessId, "wrong"))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(401);
				assertThat(e.code()).isEqualTo("invalid_password");
			});
		this.server.verify();
	}

	/** 错误透传：模组 404 + {"error":"grid_not_found"} → ModApiException(404, grid_not_found)。 */
	@Test
	void modErrorBodyIsPassedThrough() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		this.server.expect(requestTo(BASE_URL + "/grids/" + accessId + "/snapshot"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"grid_not_found\"}"));

		assertThatThrownBy(() -> this.client.getSnapshot(accessId))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(404);
				assertThat(e.code()).isEqualTo("grid_not_found");
			});
		this.server.verify();
	}

	/** 错误透传：401 unauthorized 原样抛出（由 service 翻译为 mod_unavailable）。 */
	@Test
	void unauthorizedIsPassedThroughForServiceTranslation() {
		this.server.expect(requestTo(BASE_URL + "/grids"))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"unauthorized\"}"));

		assertThatThrownBy(this.client::listGrids)
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(401);
				assertThat(e.code()).isEqualTo("unauthorized");
			});
		this.server.verify();
	}

	/** 兜底：模组错误体不是契约 JSON 时归为 internal_error，保留模组状态码。 */
	@Test
	void unparsableErrorBodyFallsBackToInternalError() {
		this.server.expect(requestTo(BASE_URL + "/grids"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("<html>boom</html>"));

		assertThatThrownBy(this.client::listGrids)
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(500);
				assertThat(e.code()).isEqualTo("internal_error");
			});
		this.server.verify();
	}

	/** 防误用：请求体序列化透明（CraftRequest 里的 JsonNode 原样出现在请求 JSON 中）。 */
	@Test
	void craftRequestBodyIsTransferredVerbatim() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		this.server.expect(requestTo(BASE_URL + "/grids/" + accessId + "/craft"))
			.andExpect(content().json("{\"key\":{\"id\":\"ae2:certus_quartz_crystal\",\"grind\":true},\"amount\":1}"))
			.andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
				.body("{\"commandId\":\"5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100\",\"state\":\"PENDING\",\"submittedAt\":1}"));

		this.client.submitCraft(accessId, new CraftRequest(key("{\"id\":\"ae2:certus_quartz_crystal\",\"grind\":true}"), 1L));

		this.server.verify();
	}

	/** 正常响应：GET /icons/{ns}/{file}.png 以 byte[] 原样读回（纯字节代理，不做 JSON 解析）。 */
	@Test
	void fetchIconReturnsRawBytes() {
		byte[] png = new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x00, 0x01 };
		this.server.expect(requestTo(BASE_URL + "/icons/minecraft/iron_block.png"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("X-Shared-Secret", "test-secret"))
			.andRespond(withSuccess(png, MediaType.IMAGE_PNG));

		byte[] bytes = this.client.fetchIcon("minecraft", "iron_block.png");

		assertThat(bytes).isEqualTo(png);
		this.server.verify();
	}

	/** 错误透传：模组 404 icon_not_found（图标未渲染，惰性补齐中）→ ModApiException(404, icon_not_found)。 */
	@Test
	void fetchIconNotFoundPassesThrough() {
		this.server.expect(requestTo(BASE_URL + "/icons/ae2/controller.png"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"icon_not_found\"}"));

		assertThatThrownBy(() -> this.client.fetchIcon("ae2", "controller.png"))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(404);
				assertThat(e.code()).isEqualTo("icon_not_found");
			});
		this.server.verify();
	}

	/** 错误透传：模组 400 invalid_icon_path（非法路径段）→ ModApiException(400, invalid_icon_path)。 */
	@Test
	void fetchIconInvalidPathPassesThrough() {
		this.server.expect(requestTo(BASE_URL + "/icons/minecraft/UPPER.png"))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"invalid_icon_path\"}"));

		assertThatThrownBy(() -> this.client.fetchIcon("minecraft", "UPPER.png"))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(400);
				assertThat(e.code()).isEqualTo("invalid_icon_path");
			});
		this.server.verify();
	}

	/** 正常响应：DELETE /commands/{commandId} → 204 无响应体（契约 v0.9，模组 0.0.13）。 */
	@Test
	void deleteCommandSendsDeleteAndAccepts204() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		UUID commandId = UUID.fromString("5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100");
		this.server.expect(requestTo(BASE_URL + "/grids/" + accessId + "/commands/" + commandId))
			.andExpect(method(HttpMethod.DELETE))
			.andExpect(header("X-Shared-Secret", "test-secret"))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		this.client.deleteCommand(accessId, commandId);

		this.server.verify();
	}

	/** 错误透传：DELETE 命令记录不存在 → ModApiException(404, command_not_found)。 */
	@Test
	void deleteCommandNotFoundPassesThrough() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		UUID commandId = UUID.fromString("5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100");
		this.server.expect(requestTo(BASE_URL + "/grids/" + accessId + "/commands/" + commandId))
			.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"command_not_found\"}"));

		assertThatThrownBy(() -> this.client.deleteCommand(accessId, commandId))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(404);
				assertThat(e.code()).isEqualTo("command_not_found");
			});
		this.server.verify();
	}

	/** 构造 AEKey JSON 的便捷方法（模拟快照里透传的 key）。 */
	private static JsonNode key(String json) {
		try {
			return new ObjectMapper().readTree(json);
		}
		catch (JacksonException e) {
			throw new IllegalStateException(e);
		}
	}
}
