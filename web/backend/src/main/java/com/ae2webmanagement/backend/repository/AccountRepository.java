package com.ae2webmanagement.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ae2webmanagement.backend.domain.Account;

/**
 * 账号表访问（H2）；认证用例（登录校验、首次启动引导建号）经由本仓库读写。
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

	/**
	 * 按用户名查找账号。
	 *
	 * @param username 登录用户名
	 * @return 命中的账号（可能为空）
	 */
	Optional<Account> findByUsername(String username);

	/**
	 * @return 账号表是否已存在任何账号
	 */
	boolean existsBy();
}
