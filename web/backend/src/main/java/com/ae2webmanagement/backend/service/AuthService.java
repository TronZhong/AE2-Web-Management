package com.ae2webmanagement.backend.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import cn.dev33.satoken.stp.StpUtil;

import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.config.AppProperties;
import com.ae2webmanagement.backend.domain.Account;
import com.ae2webmanagement.backend.dto.LoginResponse;
import com.ae2webmanagement.backend.dto.MeResponse;
import com.ae2webmanagement.backend.repository.AccountRepository;

/**
 * 账号制认证用例（契约 v0.2「认证端点」）：登录 / 注销 / 当前账号，
 * 以及首次启动时引导创建初始管理员。
 *
 * <p>语义（roadmap 已完成项，账号制认证）：密码是后端账号密码，与 Minecraft/AE2 无关；
 * 登录后可见全部已注册网络；会话由 Sa-Token 管理（satoken Cookie / 请求头）。
 * 密码只存 BCrypt 哈希；登录失败统一 {@code 401 unauthorized}，不区分
 * 「用户不存在」与「密码错误」。</p>
 */
@Service
public class AuthService implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final AccountRepository accountRepository;

	private final AppProperties properties;

	/** BCrypt 哈希与校验器（线程安全，可复用单实例）。 */
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	/**
	 * @param accountRepository 账号表访问
	 * @param properties        {@code app.*} 配置（初始管理员引导参数）
	 */
	public AuthService(AccountRepository accountRepository, AppProperties properties) {
		this.accountRepository = accountRepository;
		this.properties = properties;
	}

	/**
	 * 登录：校验用户名密码后建立 Sa-Token 会话（写 satoken Cookie）。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「POST /api/auth/login」。</p>
	 *
	 * @param username 用户名（controller 已确保非空白）
	 * @param password 密码原文
	 * @return 受理响应（token 名、token 值、用户名回显）
	 */
	public LoginResponse login(String username, String password) {
		Account account = this.accountRepository.findByUsername(username)
			.orElseThrow(AuthService::unauthorized);
		if (!this.encoder.matches(password, account.getPasswordHash())) {
			throw unauthorized();
		}
		StpUtil.login(account.getId());
		return new LoginResponse(StpUtil.getTokenName(), StpUtil.getTokenValue(), account.getUsername());
	}

	/**
	 * 注销当前会话：使当前 token 失效并清除 Cookie（契约「POST /api/auth/logout」）。
	 */
	public void logout() {
		StpUtil.logout();
	}

	/**
	 * 当前登录账号（契约「GET /api/auth/me」）；前端启动时调用以恢复会话。
	 *
	 * @return 当前用户名
	 */
	public MeResponse me() {
		long loginId = StpUtil.getLoginIdAsLong();
		return this.accountRepository.findById(loginId)
			.map((account) -> new MeResponse(account.getUsername()))
			.orElseThrow(AuthService::unauthorized);
	}

	/**
	 * 当前登录账号的数字 ID（契约 v0.3 网格访问控制使用：解锁状态按账号持久记忆）。
	 *
	 * @return 当前会话账号 ID（{@code accounts.id}）
	 */
	public long currentAccountId() {
		return StpUtil.getLoginIdAsLong();
	}

	/**
	 * 首次启动引导：账户表为空时按 {@code app.auth.admin-*} 创建初始管理员。
	 *
	 * <p>已有账号时跳过（不在本轮实现改密/建号端点）；默认密码会输出 WARN 提醒部署者替换。</p>
	 */
	@Override
	public void run(ApplicationArguments args) {
		if (this.accountRepository.existsBy()) {
			return;
		}
		Account admin = new Account();
		admin.setUsername(this.properties.auth().adminUsername());
		admin.setPasswordHash(this.encoder.encode(this.properties.auth().adminPassword()));
		admin.setCreatedAt(Instant.now());
		this.accountRepository.save(admin);
		log.info("账户表为空，已创建初始管理员账号 {}（密码来自 app.auth.admin-password 配置）", admin.getUsername());
		if ("change-me".equals(this.properties.auth().adminPassword())) {
			log.warn("初始管理员密码为默认值 change-me，部署前请修改 app.auth.admin-password 后重建账户表");
		}
	}

	/**
	 * @return 统一的登录失败异常（不区分用户不存在与密码错误）
	 */
	private static ModApiException unauthorized() {
		return new ModApiException(401, "unauthorized");
	}
}
