package com.ae2webmanagement.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.ae2webmanagement.backend.domain.GridAccess;

/**
 * 「已解锁网格」记录表访问（H2，表 {@code account_grid_access}）；
 * 网格密码访问控制（unlocked 计算、unlock 记录、清除密码撤销）经由本仓库读写。
 */
public interface GridAccessRepository extends JpaRepository<GridAccess, Long> {

	/**
	 * 判断某账号是否已解锁某网络。
	 *
	 * @param accountId 账号 ID
	 * @param accessId  网络接入 ID（UUID 字符串）
	 * @return 已存在解锁记录为 {@code true}
	 */
	boolean existsByAccountIdAndAccessId(Long accountId, String accessId);

	/**
	 * 删除某网络的全部解锁记录（幂等）：用于后端观察到网络密码被清除时
	 * 撤销所有账号的解锁状态，避免重新设密后旧记录残留放行。
	 *
	 * @param accessId 网络接入 ID（UUID 字符串）
	 */
	@Transactional
	void deleteByAccessId(String accessId);
}
