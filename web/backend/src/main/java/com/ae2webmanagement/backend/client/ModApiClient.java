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
import com.ae2webmanagement.backend.dto.ModGridListResponse;
import com.ae2webmanagement.backend.dto.SnapshotResponse;
import com.ae2webmanagement.backend.dto.VerifyPasswordRequest;

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
 *       由 {@code GridService} 翻译为 {@code 503 mod_unreachable}。</li>
 *   <li>顶层响应均为「数组包在对象字段里」的非泛型 record
 *       （{@code {grids:[...]}} / {@code {commands:[...]}}），按 class 反序列化即可，
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
	 * 查询网格列表。
	 *
	 * @return 模组 {@code GET /grids} 的响应（反序列化为模组侧 DTO，
	 *         含可空 {@code hasPassword} 以兼容旧版模组）
	 */
	public ModGridListResponse listGrids() {
		return call(() -> this.modRestClient.get()
			.uri("/grids")
			.retrieve()
			.body(ModGridListResponse.class));
	}

	/**
	 * 查询单个网格的完整快照。
	 *
	 * @param accessId 网格接入 ID（已由 service 校验为合法 UUID）
	 * @return 模组 {@code GET /grids/{accessId}/snapshot} 的响应
	 */
	public SnapshotResponse getSnapshot(UUID accessId) {
		return call(() -> this.modRestClient.get()
			.uri("/grids/{accessId}/snapshot", accessId)
			.retrieve()
			.body(SnapshotResponse.class));
	}

	/**
	 * 投递合成命令。
	 *
	 * @param accessId 网格接入 ID（已由 service 校验为合法 UUID）
	 * @param request  合成请求 {@code {key, amount}}，key 以 JsonNode 透传
	 * @return 模组 {@code POST /grids/{accessId}/craft} 的 202 响应
	 */
	public CraftAcceptedResponse submitCraft(UUID accessId, CraftRequest request) {
		return call(() -> this.modRestClient.post()
			.uri("/grids/{accessId}/craft", accessId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.retrieve()
			.body(CraftAcceptedResponse.class));
	}

	/**
	 * 查询单个网格的合成命令历史与当前状态。
	 *
	 * @param accessId 网格接入 ID（已由 service 校验为合法 UUID）
	 * @return 模组 {@code GET /grids/{accessId}/commands} 的响应
	 */
	public CommandListResponse getCommands(UUID accessId) {
		return call(() -> this.modRestClient.get()
			.uri("/grids/{accessId}/commands", accessId)
			.retrieve()
			.body(CommandListResponse.class));
	}

	/**
	 * 校验网格访问密码（后端在账号「解锁网格」时转发调用）。
	 *
	 * <p>契约条目：docs/contracts/index.md「POST /grids/{accessId}/verify-password」；
	 * 未设密码或密码匹配 → 204（无响应体），由调用方据此记录解锁。</p>
	 *
	 * @param accessId 网格接入 ID（已由 service 校验为合法 UUID）
	 * @param password 用户输入的密码原文（仅本次请求体内出现，不落盘）
	 */
	public void verifyPassword(UUID accessId, String password) {
		call(() -> this.modRestClient.post()
			.uri("/grids/{accessId}/verify-password", accessId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(new VerifyPasswordRequest(password))
			.retrieve()
			.toBodilessEntity());
	}

	/**
	 * 按字节转发模组图标静态文件（契约条目：docs/contracts/index.md
	 * 「GET /icons/{namespace}/{path}.png」，模组 0.0.10 起提供）。
	 *
	 * <p>纯字节代理：成功响应体以 {@code byte[]} 原样读回，<b>不做任何 JSON 解析</b>、
	 * 不进 service 业务层（docs/roadmap.md §5）；错误响应仍按模组契约解析
	 * {@code {"error":"code"}}（如 404 icon_not_found / 400 invalid_icon_path）。
	 * 连接失败 / 超时以 {@link ResourceAccessException} 原样抛出，由
	 * {@code GlobalExceptionHandler} 兜底翻译为 {@code 503 mod_unreachable}。</p>
	 *
	 * @param namespace 图标命名空间段（如 {@code minecraft}，已由 controller 校验字符集）
	 * @param fileName  图标文件名段（如 {@code iron_block.png}，已由 controller 校验字符集）
	 * @return PNG 文件字节（16×16 图标，数百字节量级）
	 */
	public byte[] fetchIcon(String namespace, String fileName) {
		return call(() -> this.modRestClient.get()
			.uri("/icons/{namespace}/{fileName}", namespace, fileName)
			.retrieve()
			.body(byte[].class));
	}

	/**
	 * 删除一条合成命令记录（契约条目：docs/contracts/index.md「合成命令 API」
	 * 「DELETE /grids/{accessId}/commands/{commandId}」，模组 0.0.13 起提供）。
	 *
	 * <p>删除 PENDING 记录即撤销（不会提交给 AE2）；删除 SUBMITTED/FAILED
	 * 记录仅清理历史，不影响 AE2 已受理的任务。模组在主线程原子执行删除，
	 * 响应即最终结果：204 已删除；404 grid_not_found / command_not_found；
	 * 400 invalid_access_id / invalid_command_id。</p>
	 *
	 * @param accessId  网格接入 ID（已由 service 校验为合法 UUID）
	 * @param commandId 命令 ID（已由 service 校验为合法 UUID）
	 */
	public void deleteCommand(UUID accessId, UUID commandId) {
		call(() -> this.modRestClient.delete()
			.uri("/grids/{accessId}/commands/{commandId}", accessId, commandId)
			.retrieve()
			.toBodilessEntity());
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
