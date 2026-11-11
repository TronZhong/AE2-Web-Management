package com.ae2webmanagement.backend.controller;

import java.time.Instant;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.ae2webmanagement.backend.domain.Account;
import com.ae2webmanagement.backend.repository.AccountRepository;
import com.ae2webmanagement.backend.service.PasswordHashes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账号认证全链路集成测试（契约 v0.2「认证端点」+ v0.11 登录语义）：真实
 * Sa-Token 拦截器 + 真实 {@code AuthService} + 内存 H2，覆盖「未登录 401 →
 * 登录 → 会话访问 → 注销」的完整闭环与错误码形状。
 *
 * <p>roadmap §11 起账号只能在游戏内注册，后端无引导建号——测试直接向账号表
 * 播种「游戏内注册」形状的账号（玩家名 + PBKDF2 哈希 + 绑定 playerUuid），
 * 模拟 {@code AccountSyncService} 的同步产物；登录按 PBKDF2 本地校验。</p>
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:authflow;DB_CLOSE_DELAY=-1",
})
@AutoConfigureMockMvc
class AuthFlowTests {

	private static final String PLAYER_UUID = "9b2ad6c1-4b7a-4c1e-9a3f-2f8f6f41a099";

	private static final String USERNAME = "Steve";

	private static final String PASSWORD = "test-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountRepository accountRepository;

	/** 播种一个与模组同步产物同形的账号（PBKDF2 哈希 + 绑定玩家 UUID）。 */
	@BeforeEach
	void seedAccount() {
		this.accountRepository.deleteAll();
		Account account = new Account();
		account.setUsername(USERNAME);
		account.setPlayerUuid(PLAYER_UUID);
		account.setPasswordHash(PasswordHashes.encodeForTests(PASSWORD, 60_000));
		account.setCreatedAt(Instant.now());
		this.accountRepository.save(account);
	}

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
				.content("{\"username\":\"Steve\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_json"));
	}

	/** 非 JSON 请求体 → 400 {"error":"invalid_json"}。 */
	@Test
	void loginWithNonJsonBodyIsInvalidJson() throws Exception {
		this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.TEXT_PLAIN)
				.content("Steve"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_json"));
	}

	/** 用户名或密码错误 → 401 {"error":"unauthorized"}。 */
	@Test
	void loginWithWrongCredentialsIsUnauthorized() throws Exception {
		this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"Steve\",\"password\":\"wrong\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("unauthorized"));
	}

	/** 用户名不存在 → 401 {"error":"unauthorized"}（游戏内未注册的玩家名）。 */
	@Test
	void loginWithUnknownUsernameIsUnauthorized() throws Exception {
		this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"Alex\",\"password\":\"test-password\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("unauthorized"));
	}

	/** 登录成功 → 200 {tokenName, tokenValue, username} + satoken Cookie；凭 Cookie 访问 /me。 */
	@Test
	void loginEstablishesSessionForProtectedEndpoints() throws Exception {
		MvcResult login = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"Steve\",\"password\":\"test-password\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenName").value("satoken"))
			.andExpect(jsonPath("$.username").value("Steve"))
			.andExpect(jsonPath("$.tokenValue").isNotEmpty())
			.andExpect(cookie().exists("satoken"))
			.andReturn();
		Cookie satoken = login.getResponse().getCookie("satoken");

		// /me 回显绑定的玩家名（契约 v0.11）
		this.mockMvc.perform(get("/api/auth/me").cookie(satoken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("Steve"))
			.andExpect(jsonPath("$.playerName").value("Steve"));
	}

	/** 用请求头 satoken 与 Cookie 等价（非 Cookie 客户端路径）。 */
	@Test
	void tokenHeaderIsEquivalentToCookie() throws Exception {
		MvcResult login = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"Steve\",\"password\":\"test-password\"}"))
			.andExpect(status().isOk())
			.andReturn();
		String tokenValue = login.getResponse().getCookie("satoken").getValue();

		this.mockMvc.perform(get("/api/auth/me").header("satoken", tokenValue))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("Steve"));
	}

	/** 注销响应为空对象 {}，且原 token 立即失效 → 再访问 /me 得 401。 */
	@Test
	void logoutInvalidatesSession() throws Exception {
		MvcResult login = this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"Steve\",\"password\":\"test-password\"}"))
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

	/** 玩家改名跟随（契约 v0.11）：账号同步更新用户名后，新玩家名登录成功、旧名失效。 */
	@Test
	void renamedPlayerLogsInWithNewName() throws Exception {
		this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"Steve\",\"password\":\"test-password\"}"))
			.andExpect(status().isOk());

		// 模拟 AccountSyncService 的 upsert：玩家改名 Steve → Alex
		Account account = this.accountRepository.findByPlayerUuid(PLAYER_UUID).orElseThrow();
		account.setUsername("Alex");
		this.accountRepository.save(account);

		this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"Alex\",\"password\":\"test-password\"}"))
			.andExpect(status().isOk());

		this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"Steve\",\"password\":\"test-password\"}"))
			.andExpect(status().isUnauthorized());
	}

	/** 防御：遗留 BCrypt 哈希（非 pbkdf2 前缀）校验失败 → 401（不做迁移）。 */
	@Test
	void legacyHashFailsLogin() throws Exception {
		Account account = this.accountRepository.findByPlayerUuid(PLAYER_UUID).orElseThrow();
		account.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdef");
		this.accountRepository.save(account);

		this.mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"Steve\",\"password\":\"test-password\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("unauthorized"));
	}
}
