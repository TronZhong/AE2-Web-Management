package com.ae2webmanagement.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 后端账号（账号制用户认证，见 docs/contracts/web-api.md「认证端点」v0.2）。
 *
 * <p>密码是后端账号密码，与 Minecraft/AE2 无关；数据库只存 BCrypt 哈希，
 * 明文仅出现在首次启动引导建号时的配置里（{@code app.auth.admin-password}）。</p>
 */
@Entity
@Table(name = "accounts")
public class Account {

	/** 自增主键。 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 登录用户名，全局唯一。 */
	@Column(nullable = false, unique = true, length = 64)
	private String username;

	/** BCrypt 密码哈希（spring-security-crypto 生成与校验）。 */
	@Column(nullable = false, length = 100)
	private String passwordHash;

	/** 建号时间。 */
	@Column(nullable = false)
	private Instant createdAt;

	/**
	 * @return 自增主键
	 */
	public Long getId() {
		return this.id;
	}

	/**
	 * @return 登录用户名
	 */
	public String getUsername() {
		return this.username;
	}

	/**
	 * @param username 登录用户名
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * @return BCrypt 密码哈希
	 */
	public String getPasswordHash() {
		return this.passwordHash;
	}

	/**
	 * @param passwordHash BCrypt 密码哈希
	 */
	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	/**
	 * @return 建号时间
	 */
	public Instant getCreatedAt() {
		return this.createdAt;
	}

	/**
	 * @param createdAt 建号时间
	 */
	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
