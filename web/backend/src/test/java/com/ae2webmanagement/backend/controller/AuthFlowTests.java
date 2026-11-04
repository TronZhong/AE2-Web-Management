package com.ae2webmanagement.backend.controller;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账号认证全链路集成测试（契约 v0.2「认证端点」）：真实 Sa-Token 拦截器 +
 * 真实 {@code AuthService} + 内存 H2，覆盖「未登录 401 → 登录 → 会话访问 → 注销」
 * 的完整闭环与错误码形状。密码取自本类属性（不触碰 application.yaml 默认值）。
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:authflow;DB_CLOSE_DELAY=-1",
		"app.auth.admin-username=admin",
		"app.auth.admin-password=test-password",
})
@AutoConfigureMockMvc
class AuthFlowTests {

	@Autowired
	private MockMvc mockMvc;

	/** 未登录访问受保护端点 → 401 {"error":"unauthorized"}。 */
	@Test
	void protectedEndpointRequiresLogin() throws Exception {
		this.mockMvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("unauthorized"));
	}

	/** 登录缺失字段 → 400 {"error":"invalid_json"}（不触发凭证校验）。 */
	@Test
	void loginWithMissingFieldsIsInvalidJson() throws Exception {
		this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_json"));
	}

	/** 非 JSON 请求体 → 400 {"error":"invalid_json"}。 */
	@Test
	void loginWithNonJsonBodyIsInvalidJson() throws Exception {
		this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.TEXT_PLAIN)
				.content("admin"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_json"));
	}

	/** 用户名或密码错误 → 401 {"error":"unauthorized"}。 */
	@Test
	void loginWithWrongCredentialsIsUnauthorized() throws Exception {
		this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("unauthorized"));
	}

	/** 登录成功 → 200 {tokenName, tokenValue, username} + satoken Cookie；凭 Cookie 访问 /me。 */
	@Test
	void loginEstablishesSessionForProtectedEndpoints() throws Exception {
		MvcResult login = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenName").value("satoken"))
			.andExpect(jsonPath("$.username").value("admin"))
			.andExpect(jsonPath("$.tokenValue").isNotEmpty())
			.andExpect(cookie().exists("satoken"))
			.andReturn();
		Cookie satoken = login.getResponse().getCookie("satoken");

		this.mockMvc.perform(get("/api/auth/me").cookie(satoken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"));
	}

	/** 用请求头 satoken 与 Cookie 等价（非 Cookie 客户端路径）。 */
	@Test
	void tokenHeaderIsEquivalentToCookie() throws Exception {
		MvcResult login = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
			.andExpect(status().isOk())
			.andReturn();
		String tokenValue = login.getResponse().getCookie("satoken").getValue();

		this.mockMvc.perform(get("/api/auth/me").header("satoken", tokenValue))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"));
	}

	/** 注销响应为空对象 {}，且原 token 立即失效 → 再访问 /me 得 401。 */
	@Test
	void logoutInvalidatesSession() throws Exception {
		MvcResult login = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
			.andExpect(status().isOk())
			.andReturn();
		Cookie satoken = login.getResponse().getCookie("satoken");

		this.mockMvc.perform(post("/api/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.cookie(satoken))
			.andExpect(status().isOk())
			.andExpect(content().string("{}"));

		this.mockMvc.perform(get("/api/auth/me").cookie(satoken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("unauthorized"));
	}
}
