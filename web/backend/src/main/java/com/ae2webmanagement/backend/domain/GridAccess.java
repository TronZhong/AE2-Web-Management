package com.ae2webmanagement.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 账号维度的「已解锁网格」记录（契约 v0.3 网格访问控制，见 docs/contracts/web-api.md）。
 *
 * <p>一个账号成功通过某网格的密码校验（或该网格无密码时调用 unlock）后，
 * 写入一条记录；之后该账号访问此网格不再需要密码。状态持久化在 H2，
 * 登录会话过期重新登录后依然生效。</p>
 */
@Entity
@Table(name = "account_grid_access",
		uniqueConstraints = @UniqueConstraint(columnNames = { "account_id", "access_id" }))
public class GridAccess {

	/** 自增主键。 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 解锁该网格的账号 ID（{@code accounts.id}）。 */
	@Column(name = "account_id", nullable = false)
	private Long accountId;

	/** 网格接入 ID（模组身份表的 accessId，UUID 字符串）。 */
	@Column(name = "access_id", nullable = false, length = 36)
	private String accessId;

	/** 解锁时间。 */
	@Column(name = "unlocked_at", nullable = false)
	private Instant unlockedAt;

	/**
	 * @return 自增主键
	 */
	public Long getId() {
		return this.id;
	}

	/**
	 * @return 解锁该网格的账号 ID
	 */
	public Long getAccountId() {
		return this.accountId;
	}

	/**
	 * @param accountId 解锁该网格的账号 ID
	 */
	public void setAccountId(Long accountId) {
		this.accountId = accountId;
	}

	/**
	 * @return 网格接入 ID（UUID 字符串）
	 */
	public String getAccessId() {
		return this.accessId;
	}

	/**
	 * @param accessId 网格接入 ID（UUID 字符串）
	 */
	public void setAccessId(String accessId) {
		this.accessId = accessId;
	}

	/**
	 * @return 解锁时间
	 */
	public Instant getUnlockedAt() {
		return this.unlockedAt;
	}

	/**
	 * @param unlockedAt 解锁时间
	 */
	public void setUnlockedAt(Instant unlockedAt) {
		this.unlockedAt = unlockedAt;
	}
}
