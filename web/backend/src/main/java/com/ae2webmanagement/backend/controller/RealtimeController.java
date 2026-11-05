package com.ae2webmanagement.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ae2webmanagement.backend.service.RealtimeEventHub;

/**
 * 实时事件端点：{@code GET /api/events}（SSE，text/event-stream）。
 *
 * <p>对应 docs/contracts/web-api.md「实时事件」；受 Sa-Token 拦截器保护
 * （{@code /api/**} 需登录，Cookie 或请求头 satoken 均可）。长超时 + 心跳
 * 由 {@link RealtimeEventHub} 管理，浏览器 EventSource 自带重连。</p>
 *
 * <p>事件形状（变更提示，前端收到后 refetch）：</p>
 * <ul>
 *   <li>{@code {"type":"grids-updated"}} —— 网络列表级变化；</li>
 *   <li>{@code {"type":"grid-snapshot-updated","accessId":"..."}} —— 某网络快照被刷新。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class RealtimeController {

	private final RealtimeEventHub eventHub;

	/**
	 * @param eventHub 管理 SSE emitter 集合的事件枢纽
	 */
	public RealtimeController(RealtimeEventHub eventHub) {
		this.eventHub = eventHub;
	}

	/**
	 * 订阅实时事件流：注册一个新的 {@link SseEmitter} 并立即返回。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「GET /api/events」；连接建立后由
	 * 服务端主动推送变更提示，前端收到再 refetch，轮询保留作兜底。</p>
	 *
	 * @return SSE 连接（长超时，心跳保活）
	 */
	@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events() {
		return this.eventHub.register();
	}
}
