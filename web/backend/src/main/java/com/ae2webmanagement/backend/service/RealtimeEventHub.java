package com.ae2webmanagement.backend.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import tools.jackson.databind.ObjectMapper;

/**
 * 实时推送的事件枢纽：管理所有已注册的 {@link SseEmitter}，向它们广播变更提示。
 *
 * <p>对应 docs/roadmap.md §3（SSE 实时推送）：事件形状只推「变更提示」
 * （{@code {"type":"grids-updated"}} / {@code {"type":"grid-snapshot-updated","accessId":...}}），
 * 前端收到后再 refetch，契约保持简单。广播由 {@link GridStateSyncService} 触发；
 * 本类只负责 emitter 生命周期（注册 / 移除）与心跳保活。</p>
 *
 * <p>线程安全：emitter 集合用 {@link ConcurrentHashMap#newKeySet()}；
 * 广播与心跳遍历失败（客户端断开 / 超时）时移除该 emitter。</p>
 */
@Component
public class RealtimeEventHub {

	private static final Logger log = LoggerFactory.getLogger(RealtimeEventHub.class);

	/** SSE 连接超时（毫秒）：长超时 + 心跳保活，浏览器 EventSource 自带重连。 */
	private static final long EMITTER_TIMEOUT_MS = 300_000L;

	/** 心跳间隔（毫秒）：向后端代理/浏览器发送注释行，防止连接被闲置掐断。 */
	private static final long HEARTBEAT_INTERVAL_MS = 15_000L;

	private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

	private final ObjectMapper objectMapper;

	/**
	 * @param objectMapper 序列化事件 payload 为 JSON（Jackson 3）
	 */
	public RealtimeEventHub(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * 注册一个新的 SSE 连接并返回其 emitter；连接完成 / 超时 / 出错时自动从集合移除。
	 *
	 * @return 可直接作为 {@code GET /api/events} 响应体返回的 {@link SseEmitter}
	 */
	public SseEmitter register() {
		SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
		emitter.onCompletion(() -> this.emitters.remove(emitter));
		emitter.onTimeout(() -> this.emitters.remove(emitter));
		emitter.onError((error) -> {
			this.emitters.remove(emitter);
			log.debug("SSE 连接异常，已移除: {}", error.getMessage());
		});
		this.emitters.add(emitter);
		return emitter;
	}

	/**
	 * 向全部在线连接广播一个事件 payload（序列化为 JSON {@code data} 行）。
	 *
	 * @param payload 事件对象，如 {@code Map.of("type", "grids-updated")}
	 */
	public void broadcast(Map<String, Object> payload) {
		if (this.emitters.isEmpty()) {
			return;
		}
		String json = toJson(payload);
		for (SseEmitter emitter : this.emitters) {
			try {
				emitter.send(SseEmitter.event().data(json));
			}
			catch (Exception error) {
				this.emitters.remove(emitter);
				emitter.completeWithError(error);
				log.debug("SSE 广播失败，已移除连接: {}", error.getMessage());
			}
		}
	}

	/**
	 * 定时心跳：向所有连接发送 SSE 注释行（{@code :ping}），保活并顺带清理死连接。
	 */
	@Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MS)
	public void heartbeat() {
		for (SseEmitter emitter : this.emitters) {
			try {
				emitter.send(SseEmitter.event().comment("ping"));
			}
			catch (Exception error) {
				this.emitters.remove(emitter);
				emitter.completeWithError(error);
				log.debug("SSE 心跳失败，已移除连接: {}", error.getMessage());
			}
		}
	}

	/**
	 * @param payload 事件对象
	 * @return JSON 字符串（失败时返回空对象兜底，不中断广播）
	 */
	private String toJson(Map<String, Object> payload) {
		try {
			return this.objectMapper.writeValueAsString(payload);
		}
		catch (Exception error) {
			log.warn("SSE 事件序列化失败: {}", error.getMessage());
			return "{}";
		}
	}
}
