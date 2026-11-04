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
import com.ae2webmanagement.backend.dto.ModNetworkListResponse;
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

	/** 正常响应：GET /networks 解析为 NetworkListResponse，字段一一对应（含 v0.3 hasPassword）。 */
	@Test
	void listNetworksParsesNetworksArray() {
		this.server.expect(requestTo(BASE_URL + "/networks"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("X-Shared-Secret", "test-secret"))
			.andRespond(withSuccess("""
					{"networks":[{
						"accessId":"0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001",
						"displayName":"主基地 ME 网络",
						"dimension":"minecraft:overworld",
						"controllerX":128,"controllerY":64,"controllerZ":-256,
						"online":true,
						"capturedAt":1760000000000,
						"inventoryEntries":17,
						"cpuCount":2,
						"hasPassword":true
					}]}
				""", MediaType.APPLICATION_JSON));

		ModNetworkListResponse response = this.client.listNetworks();

		assertThat(response.networks()).hasSize(1);
		assertThat(response.networks().get(0).accessId()).isEqualTo("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		assertThat(response.networks().get(0).displayName()).isEqualTo("主基地 ME 网络");
		assertThat(response.networks().get(0).dimension()).isEqualTo("minecraft:overworld");
		assertThat(response.networks().get(0).controllerX()).isEqualTo(128);
		assertThat(response.networks().get(0).controllerY()).isEqualTo(64);
		assertThat(response.networks().get(0).controllerZ()).isEqualTo(-256);
		assertThat(response.networks().get(0).online()).isTrue();
		assertThat(response.networks().get(0).capturedAt()).isEqualTo(1760000000000L);
		assertThat(response.networks().get(0).inventoryEntries()).isEqualTo(17);
		assertThat(response.networks().get(0).cpuCount()).isEqualTo(2);
		assertThat(response.networks().get(0).hasPassword()).isTrue();
		this.server.verify();
	}

	/**
	 * 向后兼容：旧版模组（&lt;0.0.6）列表无 hasPassword 字段 → 反序列化为
	 * {@code null}（service 按「无密码 = 公开」归一，无需特判）。
	 */
	@Test
	void legacyNetworkListWithoutHasPasswordDefaultsToNull() {
		this.server.expect(requestTo(BASE_URL + "/networks"))
			.andRespond(withSuccess("""
					{"networks":[{
						"accessId":"0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001",
						"displayName":"旧版模组网络",
						"dimension":"minecraft:overworld",
						"controllerX":1,"controllerY":2,"controllerZ":3,
						"online":false,
						"capturedAt":0,
						"inventoryEntries":0,
						"cpuCount":0
					}]}
				""", MediaType.APPLICATION_JSON));

		ModNetworkListResponse response = this.client.listNetworks();

		assertThat(response.networks().get(0).hasPassword()).isNull();
		this.server.verify();
	}

	/** 正常响应：GET /networks/{id}/snapshot 解析嵌套结构，key 为 JsonNode 透传。 */
	@Test
	void getSnapshotParsesNestedStructure() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		this.server.expect(requestTo(BASE_URL + "/networks/" + accessId + "/snapshot"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
					{
						"accessId":"0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001",
						"capturedAt":1760000000000,
						"online":true,
						"inventory":[{"key":{"id":"minecraft:stone"},"amount":12844}],
						"craftingCpus":[{"name":"","busy":false,"availableStorage":16384,"coProcessors":0}],
						"patterns":[{
							"primaryOutput":{"key":{"id":"minecraft:stick"},"amount":4},
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
		assertThat(response.craftingCpus().get(0).name()).isEmpty();
		assertThat(response.craftingCpus().get(0).availableStorage()).isEqualTo(16384);
		assertThat(response.patterns().get(0).primaryOutput().key().get("id").asText()).isEqualTo("minecraft:stick");
		assertThat(response.patterns().get(0).inputs().get(0).multiplier()).isEqualTo(1);
		assertThat(response.patterns().get(0).inputs().get(0).possibleInputs().get(0).amount()).isEqualTo(2);
		this.server.verify();
	}

	/** 正常响应：POST /craft 以 JSON 提交 {key, amount} 并解析 202 受理体。 */
	@Test
	void submitCraftSendsKeyAndAmountParsesAccepted() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		this.server.expect(requestTo(BASE_URL + "/networks/" + accessId + "/craft"))
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
		this.server.expect(requestTo(BASE_URL + "/networks/" + accessId + "/commands"))
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
		this.server.expect(requestTo(BASE_URL + "/networks/" + accessId + "/verify-password"))
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
		this.server.expect(requestTo(BASE_URL + "/networks/" + accessId + "/verify-password"))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"invalid_password\"}"));

		assertThatThrownBy(() -> this.client.verifyPassword(accessId, "wrong"))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(401);
				assertThat(e.code()).isEqualTo("invalid_password");
			});
		this.server.verify();
	}

	/** 错误透传：模组 404 + {"error":"network_not_found"} → ModApiException(404, network_not_found)。 */
	@Test
	void modErrorBodyIsPassedThrough() {
		UUID accessId = UUID.fromString("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001");
		this.server.expect(requestTo(BASE_URL + "/networks/" + accessId + "/snapshot"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"network_not_found\"}"));

		assertThatThrownBy(() -> this.client.getSnapshot(accessId))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(404);
				assertThat(e.code()).isEqualTo("network_not_found");
			});
		this.server.verify();
	}

	/** 错误透传：401 unauthorized 原样抛出（由 service 翻译为 mod_unavailable）。 */
	@Test
	void unauthorizedIsPassedThroughForServiceTranslation() {
		this.server.expect(requestTo(BASE_URL + "/networks"))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"unauthorized\"}"));

		assertThatThrownBy(this.client::listNetworks)
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(401);
				assertThat(e.code()).isEqualTo("unauthorized");
			});
		this.server.verify();
	}

	/** 兜底：模组错误体不是契约 JSON 时归为 internal_error，保留模组状态码。 */
	@Test
	void unparsableErrorBodyFallsBackToInternalError() {
		this.server.expect(requestTo(BASE_URL + "/networks"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("<html>boom</html>"));

		assertThatThrownBy(this.client::listNetworks)
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
		this.server.expect(requestTo(BASE_URL + "/networks/" + accessId + "/craft"))
			.andExpect(content().json("{\"key\":{\"id\":\"ae2:certus_quartz_crystal\",\"grind\":true},\"amount\":1}"))
			.andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
				.body("{\"commandId\":\"5b0cdd1e-6e0e-4a1f-9a1b-3f0f9b21b100\",\"state\":\"PENDING\",\"submittedAt\":1}"));

		this.client.submitCraft(accessId, new CraftRequest(key("{\"id\":\"ae2:certus_quartz_crystal\",\"grind\":true}"), 1L));

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
