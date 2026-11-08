package com.ae2webmanagement.backend.client;

/**
 * 模组调用链路上的统一业务异常：携带最终要返回给前端的 HTTP 状态码与机器可读错误码。
 *
 * <p>产生来源：</p>
 * <ul>
 *   <li>{@link ModApiClient}：模组返回非 2xx 时按响应体 {@code {"error":"code"}} 抛出；</li>
 *   <li>{@code GridService}：accessId 非法、连接失败翻译（mod_unreachable）、
 *       401 翻译（mod_unavailable）；</li>
 *   <li>controller：合成请求体校验（missing_key_or_amount / invalid_amount / invalid_key）。</li>
 * </ul>
 *
 * <p>由 {@code GlobalExceptionHandler} 统一输出为
 * {@code {"error":"machine_readable_code"}}，controller 内不做 try/catch。</p>
 */
public final class ModApiException extends RuntimeException {

	/** 应返回给前端的 HTTP 状态码。 */
	private final int status;

	/** 机器可读错误码，对齐 docs/contracts/web-api.md 的错误码集合。 */
	private final String code;

	/**
	 * @param status 应返回给前端的 HTTP 状态码
	 * @param code   机器可读错误码
	 */
	public ModApiException(int status, String code) {
		this(status, code, null);
	}

	/**
	 * @param status 应返回给前端的 HTTP 状态码
	 * @param code   机器可读错误码
	 * @param cause  底层原因（用于日志排查，不外露给前端）
	 */
	public ModApiException(int status, String code, Throwable cause) {
		super("mod api error: status=%d, code=%s".formatted(status, code), cause);
		this.status = status;
		this.code = code;
	}

	/** @return 应返回给前端的 HTTP 状态码 */
	public int status() {
		return this.status;
	}

	/** @return 机器可读错误码 */
	public String code() {
		return this.code;
	}
}
