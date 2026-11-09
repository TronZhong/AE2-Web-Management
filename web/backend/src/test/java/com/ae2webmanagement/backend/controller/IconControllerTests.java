package com.ae2webmanagement.backend.controller;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import com.ae2webmanagement.backend.client.ModApiClient;
import com.ae2webmanagement.backend.client.ModApiException;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link IconController} MockMvc 测试（契约 v0.7「图标转发端点」）：
 * 断言 200 响应头（image/png + 一天缓存）、路径字符集校验（400 invalid_icon_path
 * 且不触达模组）、模组错误透传（404 icon_not_found）与模组不可达兜底
 * （503 mod_unreachable，经 GlobalExceptionHandler）。
 *
 * <p>切片不加载 Sa-Token 自动配置，{@code app.auth.enabled=false} 关闭登录拦截；
 * 认证链路由 {@code AuthFlowTests} 全上下文覆盖。</p>
 */
@WebMvcTest(value = IconController.class, properties = "app.auth.enabled=false")
class IconControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ModApiClient modApiClient;

	/** 200 正常路径：字节原样透传，Content-Type 与 Cache-Control 符合契约 v0.7。 */
	@Test
	void iconIsProxiedAsBytesWithCacheHeaders() throws Exception {
		byte[] png = "fake-png-bytes".getBytes(StandardCharsets.ISO_8859_1);
		when(this.modApiClient.fetchIcon("minecraft", "iron_block.png")).thenReturn(png);

		this.mockMvc.perform(get("/api/icons/minecraft/iron_block.png"))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/png"))
			.andExpect(content().bytes(png))
			.andExpect(header().string("Cache-Control", "max-age=86400, public"));
	}

	/** 校验对齐：非法字符（大写）与编码的相对段（%2e%2e 解码后进入路径变量）→ 400 invalid_icon_path，不触达模组。 */
	@Test
	void invalidPathSegmentIsRejectedBeforeForwarding() throws Exception {
		this.mockMvc.perform(get("/api/icons/Minecraft/iron_block.png"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_icon_path"));
		this.mockMvc.perform(get("/api/icons/%2e%2e/secret.png"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_icon_path"));

		verify(this.modApiClient, never()).fetchIcon(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString());
	}

	/** 校验对齐：文件名不以 .png 结尾 → 400 invalid_icon_path。 */
	@Test
	void nonPngFileNameIsRejected() throws Exception {
		this.mockMvc.perform(get("/api/icons/minecraft/iron_block.jpg"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_icon_path"));
	}

	/** 错误透传：模组 404 icon_not_found（未渲染，惰性补齐中）→ 404 原样输出。 */
	@Test
	void iconNotFoundPassesThrough() throws Exception {
		when(this.modApiClient.fetchIcon("ae2", "controller.png"))
			.thenThrow(new ModApiException(404, "icon_not_found"));

		this.mockMvc.perform(get("/api/icons/ae2/controller.png"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("icon_not_found"));
	}

	/** 兜底翻译：模组不可达（连接失败）→ 503 mod_unreachable（GlobalExceptionHandler）。 */
	@Test
	void modUnreachableIsRenderedAsContractErrorBody() throws Exception {
		when(this.modApiClient.fetchIcon("minecraft", "stone.png"))
			.thenThrow(new ResourceAccessException("Connection refused"));

		this.mockMvc.perform(get("/api/icons/minecraft/stone.png"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.error").value("mod_unreachable"));
	}
}
