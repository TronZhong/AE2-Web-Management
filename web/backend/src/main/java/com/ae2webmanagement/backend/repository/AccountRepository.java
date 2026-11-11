package com.ae2webmanagement.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ae2webmanagement.backend.domain.Account;

/**
 * 账号表访问（H2）；账号数据由 {@code AccountSyncService} 按模组
 * {@code GET /accounts} 同步写入，认证用例（登录校验、/me）经由本仓库读取。
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

	/**
	 * 按用户名查找账号。
	 *
	 * @param username 登录用户名（游戏内玩家名）
	 * @return 命中的账号（可能为空）
	 */
	Optional<Account> findByUsername(String username);

	/**
	 * 按绑定的游戏内玩家 UUID 查找账号（账号同步 upsert 与 owner 免密判定用）。
	 *
	 * @param playerUuid 绑定玩家的 UUID
	 * @return 命中的账号（可能为空）
	 */
	Optional<Account> findByPlayerUuid(String playerUuid);

	/**
	 * @return 账号表是否已存在任何账号
	 */
	boolean existsBy();
}
