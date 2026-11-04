package com.ae2webmanagement.backend.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import cn.dev33.satoken.exception.NotLoginException;

import com.ae2webmanagement.backend.client.ModApiException;

/**
 * 全局异常兜底：把异常统一翻译为契约错误体 {@code {"error":"machine_readable_code"}}，
 * controller 内不做 try/catch。
 *
 * <p>错误码集合见 docs/contracts/web-api.md「通用约定」；其中
 * {@code invalid_json} / {@code method_not_allowed} / {@code not_found}
 * 与模组契约错误码对齐，{@code mod_unreachable} / {@code mod_unavailable}
 * 为后端新增，{@code unauthorized} 另用于账号认证（v0.2）。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * 服务/客户端抛出的业务异常：按携带的状态码与错误码原样输出。
	 *
	 * @param e 业务异常（状态码 + 错误码已由抛出方确定）
	 * @return {@code <status> {"error": code}}
	 */
	@ExceptionHandler(ModApiException.class)
	ResponseEntity<Map<String, String>> handleModApi(ModApiException e) {
		return error(HttpStatus.valueOf(e.status()), e.code());
	}

	/**
	 * 未登录 / 会话过期访问受保护端点（Sa-Token 拦截器与 {@code GET /me} 抛出）：
	 * 对齐契约 {@code 401 {"error":"unauthorized"}}，前端登录页凭此跳转。
	 *
	 * @param e Sa-Token 未登录异常
	 * @return {@code 401 {"error":"unauthorized"}}
	 */
	@ExceptionHandler(NotLoginException.class)
	ResponseEntity<Map<String, String>> handleNotLogin(NotLoginException e) {
		return error(HttpStatus.UNAUTHORIZED, "unauthorized");
	}

	/**
	 * 请求体不是合法 JSON（或缺失）：对齐模组 {@code 400 invalid_json}。
	 *
	 * @param e 消息不可读异常
	 * @return {@code 400 {"error":"invalid_json"}}
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException e) {
		return error(HttpStatus.BAD_REQUEST, "invalid_json");
	}

	/**
	 * 请求体 Content-Type 不被支持（如 text/plain 打到 JSON 端点）：
	 * 语义同「请求体形状不符」，归一为 {@code 400 invalid_json} 而非 500。
	 *
	 * @param e 媒体类型不支持异常
	 * @return {@code 400 {"error":"invalid_json"}}
	 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<Map<String, String>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
		return error(HttpStatus.BAD_REQUEST, "invalid_json");
	}

	/**
	 * HTTP 方法不被允许：对齐模组 {@code 405 method_not_allowed}。
	 *
	 * @param e 方法不支持异常
	 * @return {@code 405 {"error":"method_not_allowed"}}
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<Map<String, String>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
		return error(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed");
	}

	/**
	 * 路径不存在：对齐模组 {@code 404 not_found}。
	 *
	 * @param e 无资源异常
	 * @return {@code 404 {"error":"not_found"}}
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<Map<String, String>> handleNoResource(NoResourceFoundException e) {
		return error(HttpStatus.NOT_FOUND, "not_found");
	}

	/**
	 * 兜底：未预期异常一律 {@code 500 internal_error}，并记录 ERROR 日志。
	 *
	 * @param e 未预期异常
	 * @return {@code 500 {"error":"internal_error"}}
	 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
		log.error("未处理异常", e);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error");
	}

	/**
	 * 组装契约错误响应。
	 *
	 * @param status HTTP 状态码
	 * @param code   机器可读错误码
	 * @return {@code {"error": code}} 响应体
	 */
	private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code) {
		return ResponseEntity.status(status).body(Map.of("error", code));
	}
}
