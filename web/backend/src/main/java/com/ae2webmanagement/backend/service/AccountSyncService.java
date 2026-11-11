package com.ae2webmanagement.backend.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import com.ae2webmanagement.backend.client.ModApiClient;
import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.domain.Account;
import com.ae2webmanagement.backend.dto.ModAccountEntry;
import com.ae2webmanagement.backend.repository.AccountRepository;

/**
 * 游戏内账号同步（roadmap §11 账号制；契约条目 docs/contracts/index.md「账号 API」）。
 *
 * <p>账号只能在游戏内接入方块 GUI 注册；本服务在启动 + 定时拉取模组
 * {@code GET /accounts}，按 {@code playerUuid} upsert 到后端账号表，使登录在
 * 后端**本地**校验（游戏未启动时网页仍可登录，登录是唯一不依赖游戏的端点）。</p>
 *
 * <p>同步语义：</p>
 * <ul>
 *   <li>新账号 → 以玩家名为用户名建档（createdAt 取模组注册时间）；</li>
 *   <li>已有账号 → 玩家名与密码哈希跟随模组更新（玩家改名 / 游戏内改密后网页自动跟随）；</li>
 *   <li>玩家名与其他账号的用户名冲突（理论上仅与遗留 admin 账号冲突）→ 跳过该条并 WARN，
 *       避免唯一约束失败（存量数据按 roadmap §11 直接删库重建，不做迁移）。</li>
 * </ul>
 *
 * <p>模组不可达 / 共享密钥不匹配时静默跳过本轮（保留现有账号数据）。</p>
 */
@Service
public class AccountSyncService {

	private static final Logger log = LoggerFactory.getLogger(AccountSyncService.class);

	/** 同步轮询间隔（毫秒）。 */
	private static final long SYNC_INTERVAL_MS = 10_000L;

	/** 启动后的首次同步延迟（毫秒）：让模组先启动完成为宜，失败也不影响后续轮次。 */
	private static final long INITIAL_DELAY_MS = 3_000L;

	private final ModApiClient modApiClient;

	private final AccountRepository accountRepository;

	/**
	 * @param modApiClient       访问模组的唯一入口（{@code GET /accounts}）
	 * @param accountRepository  账号表访问（按 playerUuid upsert）
	 */
	public AccountSyncService(ModApiClient modApiClient, AccountRepository accountRepository) {
		this.modApiClient = modApiClient;
		this.accountRepository = accountRepository;
	}

	/**
	 * 定时同步：拉取模组账号列表 → 按 playerUuid upsert。
	 *
	 * <p>模组不可达（游戏未启动 / 共享密钥不匹配 / 超时）或旧版模组无该端点
	 * （404）时静默跳过，不更新本地账号。</p>
	 */
	@Scheduled(initialDelay = INITIAL_DELAY_MS, fixedDelay = SYNC_INTERVAL_MS)
	@Transactional
	public void sync() {
		List<ModAccountEntry> accounts;
		try {
			accounts = this.modApiClient.listAccounts().accounts();
		}
		catch (ModApiException e) {
			log.debug("账号同步跳过：模组调用失败（{}）", e.code());
			return;
		}
		catch (ResourceAccessException e) {
			log.debug("账号同步跳过：模组不可达（{}）", e.getMessage());
			return;
		}
		if (accounts == null) {
			return;
		}
		for (ModAccountEntry entry : accounts) {
			if (entry.playerUuid() == null || entry.playerUuid().isBlank()) {
				log.warn("账号同步跳过：模组返回的账号缺少 playerUuid（playerName={}）", entry.playerName());
				continue;
			}
			upsert(entry);
		}
	}

	/**
	 * 按 {@code playerUuid} upsert 单个账号：新账号建档；已有账号跟随玩家名与
	 * 密码哈希（游戏内注册与后续变化对网页透明）。
	 *
	 * @param entry 模组侧账号记录（含 PBKDF2 哈希，明文不出模组）
	 */
	private void upsert(ModAccountEntry entry) {
		boolean conflict = this.accountRepository.findByUsername(entry.playerName())
			.filter((other) -> !entry.playerUuid().equals(other.getPlayerUuid()))
			.isPresent();
		if (conflict) {
			log.warn("账号同步跳过：玩家名 {} 与其他账号的用户名冲突（playerUuid={}）",
					entry.playerName(), entry.playerUuid());
			return;
		}

		Account existing = this.accountRepository.findByPlayerUuid(entry.playerUuid()).orElse(null);
		Account account = existing == null ? new Account() : existing;
		account.setPlayerUuid(entry.playerUuid());
		account.setUsername(entry.playerName());
		account.setPasswordHash(entry.passwordHash());
		if (existing == null) {
			Instant createdAt = entry.createdAt() > 0
					? Instant.ofEpochMilli(entry.createdAt())
					: Instant.now();
			account.setCreatedAt(createdAt);
			log.info("同步游戏内账号：{}（playerUuid={}）", entry.playerName(), entry.playerUuid());
		}
		this.accountRepository.save(account);
	}
}
