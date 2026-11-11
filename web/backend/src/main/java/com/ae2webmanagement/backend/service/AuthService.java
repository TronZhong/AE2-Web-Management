package com.ae2webmanagement.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cn.dev33.satoken.stp.StpUtil;

import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.domain.Account;
import com.ae2webmanagement.backend.dto.LoginResponse;
import com.ae2webmanagement.backend.dto.MeResponse;
import com.ae2webmanagement.backend.repository.AccountRepository;

/**
 * 账号制认证用例（契约 v0.2「认证端点」；v0.11 起账号真源在游戏内）：
 * 登录 / 注销 / 当前账号。
 *
 * <p>语义（roadmap §11）：账号只能在**游戏内接入方块 GUI 注册**（网页无注册口），
 * 本表由 {@link AccountSyncService} 按模组 {@code GET /accounts} 同步；登录用户名
 * 即游戏内玩家名（绑定 playerUuid），密码哈希为模组同步来的 PBKDF2 哈希，
 * 由 {@link PasswordHashes} 在后端本地校验——游戏未启动时网页仍可登录
 * （登录是唯一不依赖游戏的端点）。登录失败统一 {@code 401 unauthorized}，
 * 不区分「用户不存在」与「密码错误」。</p>
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final AccountRepository accountRepository;

	/**
	 * @param accountRepository 账号表访问（数据由账号同步服务写入）
	 */
	public AuthService(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	/**
	 * 登录：校验用户名密码后建立 Sa-Token 会话（写 satoken Cookie）。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「POST /api/auth/login」；
	 * 密码按模组同步来的 PBKDF2 哈希本地校验（{@link PasswordHashes#matches}）。</p>
	 *
	 * @param username 用户名（controller 已确保非空白；即游戏内玩家名）
	 * @param password 密码原文
	 * @return 受理响应（token 名、token 值、用户名回显）
	 */
	public LoginResponse login(String username, String password) {
		Account account = this.accountRepository.findByUsername(username)
			.orElseThrow(AuthService::unauthorized);
		if (!PasswordHashes.matches(password, account.getPasswordHash())) {
			throw unauthorized();
		}
		StpUtil.login(account.getId());
		log.info("登录成功：{}（playerUuid={}）", account.getUsername(), account.getPlayerUuid());
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
	 * @return 当前用户名与绑定的游戏内玩家名（遗留无绑定账号的 playerName 为 null）
	 */
	public MeResponse me() {
		long loginId = StpUtil.getLoginIdAsLong();
		return this.accountRepository.findById(loginId)
			.map((account) -> new MeResponse(account.getUsername(),
					account.getPlayerUuid() == null ? null : account.getUsername()))
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
	 * @return 统一的登录失败异常（不区分用户不存在与密码错误）
	 */
	private static ModApiException unauthorized() {
		return new ModApiException(401, "unauthorized");
	}
}
