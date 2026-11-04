package com.ae2webmanagement.backend.client;

import java.util.UUID;
import java.util.function.Supplier;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.ae2webmanagement.backend.dto.CommandListResponse;
import com.ae2webmanagement.backend.dto.CraftAcceptedResponse;
import com.ae2webmanagement.backend.dto.CraftRequest;
import com.ae2webmanagement.backend.dto.NetworkListResponse;
import com.ae2webmanagement.backend.dto.SnapshotResponse;

/**
 * 后端与模组本地 API 之间的唯一通信入口。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>所有对模组的调用都经由本类，service / controller 中禁止直接使用 {@link RestClient}。</li>
 *   <li>模组端点只绑定回环地址，通过 {@code X-Shared-Secret} 头做机器级认证
 *       （默认头在 {@code ModClientConfig} 的 RestClient bean 上统一设置）。</li>
 *   <li>模组返回非 2xx 时抛出 {@link ModApiException}（携带 HTTP 状态 + 错误码，
 *       错误码取自模组错误体 {@code {"error":"code"}}）。</li>
 *   <li>连接失败 / 超时以 {@link ResourceAccessException} 原样抛出，
 *       由 {@code NetworkService} 翻译为 {@code 503 mod_unreachable}。</li>
 *   <li>顶层响应均为「数组包在对象字段里」的非泛型 record
 *       （{@code {networks:[...]}} / {@code {commands:[...]}}），按 class 反序列化即可，
 *       无泛型擦除问题，无需 {@code ParameterizedTypeReference}。</li>
 * </ul>
 */
@Component
public class ModApiClient {

	private final RestClient modRestClient;

	private final ObjectMapper objectMapper;

	/**
	 * @param modRestClient {@code ModClientConfig} 组装的模组 RestClient（baseUrl + 共享密钥头 + 超时）
	 * @param objectMapper  解析模组错误响应用
	 */
	public ModApiClient(RestClient modRestClient, ObjectMapper objectMapper) {
		this.modRestClient = modRestClient;
		this.objectMapper = objectMapper;
	}

	/**
	 * 查询网络列表。
	 *
	 * @return 模组 {@code GET /networks} 的响应
	 */
	public NetworkListResponse listNetworks() {
		return call(() -> this.modRestClient.get()
			.uri("/networks")
			.retrieve()
			.body(NetworkListResponse.class));
	}

	/**
	 * 查询单个网络的完整快照。
	 *
	 * @param accessId 网络接入 ID（已由 service 校验为合法 UUID）
	 * @return 模组 {@code GET /networks/{accessId}/snapshot} 的响应
	 */
	public SnapshotResponse getSnapshot(UUID accessId) {
		return call(() -> this.modRestClient.get()
			.uri("/networks/{accessId}/snapshot", accessId)
			.retrieve()
			.body(SnapshotResponse.class));
	}

	/**
	 * 投递合成命令。
	 *
	 * @param accessId 网络接入 ID（已由 service 校验为合法 UUID）
	 * @param request  合成请求 {@code {key, amount}}，key 以 JsonNode 透传
	 * @return 模组 {@code POST /networks/{accessId}/craft} 的 202 响应
	 */
	public CraftAcceptedResponse submitCraft(UUID accessId, CraftRequest request) {
		return call(() -> this.modRestClient.post()
			.uri("/networks/{accessId}/craft", accessId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.retrieve()
			.body(CraftAcceptedResponse.class));
	}

	/**
	 * 查询单个网络的合成命令历史与当前状态。
	 *
	 * @param accessId 网络接入 ID（已由 service 校验为合法 UUID）
	 * @return 模组 {@code GET /networks/{accessId}/commands} 的响应
	 */
	public CommandListResponse getCommands(UUID accessId) {
		return call(() -> this.modRestClient.get()
			.uri("/networks/{accessId}/commands", accessId)
			.retrieve()
			.body(CommandListResponse.class));
	}

	/**
	 * 统一执行一次对模组的 HTTP 调用并做异常归类。
	 *
	 * <p>非 2xx → {@link #toModApiException(RestClientResponseException)}；
	 * 连接/读取失败（{@link ResourceAccessException}）原样抛给 service 翻译；
	 * 其余 {@link RestClientException}（如 2xx 响应体不可解析，属契约破坏）
	 * 归类为 {@code 500 internal_error}。</p>
	 *
	 * @param exchange 实际发起请求的 lambda
	 * @param <T>      响应 DTO 类型
	 * @return 反序列化后的响应 DTO
	 */
	private <T> T call(Supplier<T> exchange) {
		try {
			return exchange.get();
		}
		catch (ResourceAccessException e) {
			throw e;
		}
		catch (RestClientResponseException e) {
			throw toModApiException(e);
		}
		catch (RestClientException e) {
			throw new ModApiException(500, "internal_error", e);
		}
	}

	/**
	 * 把模组的非 2xx 响应转换为 {@link ModApiException}。
	 *
	 * <p>错误码取自模组错误体 {@code {"error":"code"}}；响应体无法解析时
	 * （理论上模组契约不会出现）退回 {@code internal_error}，状态码保留模组原值。</p>
	 *
	 * @param e RestClient 抛出的非 2xx 响应异常
	 * @return 携带状态码与错误码的业务异常
	 */
	private ModApiException toModApiException(RestClientResponseException e) {
		return new ModApiException(e.getStatusCode().value(), readErrorCode(e.getResponseBodyAsString()), e);
	}

	/**
	 * 从模组错误响应体中提取 {@code error} 字段。
	 *
	 * @param body 模组错误响应体原文
	 * @return 机器可读错误码；解析失败时返回 {@code internal_error}
	 */
	private String readErrorCode(String body) {
		try {
			JsonNode node = this.objectMapper.readTree(body);
			if (node != null && node.has("error") && node.get("error").isString()) {
				return node.get("error").asString();
			}
		}
		catch (Exception ignored) {
			// 模组错误体不是预期 JSON 时退回 internal_error
		}
		return "internal_error";
	}
}
