package com.ae2webmanagement.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 后端账号（账号制用户认证，见 docs/contracts/web-api.md「认证端点」；
 * roadmap §11 起账号真源在游戏内：账号只能游戏内接入方块 GUI 注册，
 * 本表由 {@code AccountSyncService} 按模组 {@code GET /accounts} 同步）。
 *
 * <p>登录用户名即游戏内玩家名（绑定 playerUuid，玩家改名后跟随）；密码哈希
 * 为模组同步来的 PBKDF2 哈希（{@code pbkdf2:<迭代>:<盐B64>:<哈希B64>}，
 * 明文不出模组，后端本地校验）。</p>
 */
@Entity
@Table(name = "accounts")
public class Account {

	/** 自增主键。 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 登录用户名，全局唯一（v0.11 起即游戏内玩家名）。 */
	@Column(nullable = false, unique = true, length = 64)
	private String username;

	/**
	 * 绑定的游戏内玩家 UUID（模组账号表唯一键；账号同步的对齐键，
	 * owner 免密判定用）。遗留引导账号无该字段（null），按 roadmap §11
	 * 删库重建、不做迁移。
	 */
	@Column(unique = true, length = 36)
	private String playerUuid;

	/** PBKDF2 密码哈希（模组 GridPasswords 同步而来，本地 {@code PasswordHashes} 校验）。 */
	@Column(nullable = false, length = 128)
	private String passwordHash;

	/** 建号时间（同步新账号时取模组注册时间）。 */
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
	 * @return 绑定的游戏内玩家 UUID（遗留账号为 null）
	 */
	public String getPlayerUuid() {
		return this.playerUuid;
	}

	/**
	 * @param playerUuid 绑定的游戏内玩家 UUID
	 */
	public void setPlayerUuid(String playerUuid) {
		this.playerUuid = playerUuid;
	}

	/**
	 * @return PBKDF2 密码哈希
	 */
	public String getPasswordHash() {
		return this.passwordHash;
	}

	/**
	 * @param passwordHash PBKDF2 密码哈希
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
