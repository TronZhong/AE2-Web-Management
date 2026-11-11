package com.ae2webmanagement.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PasswordHashes} 单元测试（roadmap §11）：PBKDF2 哈希本地校验与
 * 模组 {@code GridPasswords} 存储格式（{@code pbkdf2:<迭代>:<盐B64>:<哈希B64>}）
 * 的对齐——正确密码匹配、错误密码 / 旧格式哈希 / 畸形输入一律失败。
 */
class PasswordHashesTests {

	private static final String PASSWORD = "s3cret-密码";

	/** 正确密码匹配模组同格式哈希（不同迭代次数均可解析）。 */
	@Test
	void correctPasswordMatchesModFormatHash() {
		String stored = PasswordHashes.encodeForTests(PASSWORD, 60_000);
		assertThat(stored).startsWith("pbkdf2:60000:");
		assertThat(PasswordHashes.matches(PASSWORD, stored)).isTrue();
	}

	/** 错误密码 → false。 */
	@Test
	void wrongPasswordFails() {
		String stored = PasswordHashes.encodeForTests(PASSWORD, 60_000);
		assertThat(PasswordHashes.matches("wrong", stored)).isFalse();
	}

	/** 旧版引导账号的 BCrypt 哈希（$2 前缀）不匹配 pbkdf2 格式 → false。 */
	@Test
	void legacyBcryptHashFails() {
		assertThat(PasswordHashes.matches(PASSWORD, "$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345")).isFalse();
	}

	/** 畸形输入（缺段 / 非法 Base64 / null）一律 false，不抛异常。 */
	@Test
	void malformedHashesFail() {
		assertThat(PasswordHashes.matches(PASSWORD, "pbkdf2:60000")).isFalse();
		assertThat(PasswordHashes.matches(PASSWORD, "pbkdf2:abc:c2FsdA==:aGFzaA==")).isFalse();
		assertThat(PasswordHashes.matches(PASSWORD, "pbkdf2:60000:!!!:aGFzaA==")).isFalse();
		assertThat(PasswordHashes.matches(PASSWORD, "")).isFalse();
		assertThat(PasswordHashes.matches(PASSWORD, null)).isFalse();
		assertThat(PasswordHashes.matches(null, "pbkdf2:60000:c2FsdA==:aGFzaA==")).isFalse();
	}

	/** 常量时间比较工具：等值 true、异值 false、null false。 */
	@Test
	void constantTimeEqualsBasics() {
		assertThat(PasswordHashes.constantTimeEquals("abc", "abc")).isTrue();
		assertThat(PasswordHashes.constantTimeEquals("abc", "abd")).isFalse();
		assertThat(PasswordHashes.constantTimeEquals(null, "abc")).isFalse();
	}
}
