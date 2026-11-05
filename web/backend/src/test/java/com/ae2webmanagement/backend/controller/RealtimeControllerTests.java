package com.ae2webmanagement.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ae2webmanagement.backend.service.RealtimeEventHub;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RealtimeController} MockMvc 测试：{@code GET /api/events} 注册 SSE
 * 连接并返回 text/event-stream（docs/roadmap.md §3 实时推送）。
 *
 * <p>切片不加载 Sa-Token 自动配置，{@code app.auth.enabled=false} 关闭登录拦截；
 * 认证保护由 {@link AuthFlowTests} 全上下文覆盖。真实事件广播语义在
 * {@link GridStateSyncServiceTests} / {@link RealtimeEventHub} 覆盖。</p>
 */
@WebMvcTest(value = RealtimeController.class, properties = "app.auth.enabled=false")
class RealtimeControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RealtimeEventHub eventHub;

	/** GET /api/events：返回 SseEmitter，响应为 text/event-stream（异步连接建立）。 */
	@Test
	void eventsReturnsSseStream() throws Exception {
		SseEmitter emitter = new SseEmitter();
		when(this.eventHub.register()).thenReturn(emitter);

		MvcResult result = this.mockMvc.perform(get("/api/events"))
			.andExpect(request().asyncStarted())
			.andReturn();

		// 主动完成 emitter，使 async 结果可用后再 dispatch（模拟连接关闭）
		emitter.complete();

		this.mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
	}
}
