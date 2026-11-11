package com.ae2webmanagement.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2 密码哈希本地校验（roadmap §11 账号制）。
 *
 * <p>存储格式与模组 {@code GridPasswords} 完全对齐：
 * {@code pbkdf2:<迭代>:<盐B64>:<哈希B64>}（PBKDF2WithHmacSHA256）。密码明文
 * 从不出模组——游戏内注册后只同步哈希，登录校验在后端本地完成，因此游戏未
 * 启动时网页仍可登录（登录是唯一不依赖游戏的端点）。与模组实现相互独立
 * （不共享类，仅通过契约格式对齐）。</p>
 *
 * <p>线程安全：{@link SecretKeyFactory} 每次调用新建；旧版引导账号的 BCrypt
 * 哈希（{@code $2…} 前缀）不匹配本格式，一律校验失败（存量数据按 roadmap §11
 * 直接删库重建，不做迁移）。</p>
 */
public final class PasswordHashes {

	/** 存储格式前缀（与模组 GridPasswords 一致）。 */
	private static final String PREFIX = "pbkdf2:";

	private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

	private PasswordHashes() {
	}

	/**
	 * 校验密码原文与存储哈希是否匹配。
	 *
	 * @param rawPassword 用户输入的密码原文
	 * @param storedHash  存储哈希（模组同步来的 {@code pbkdf2:…} 格式）
	 * @return 匹配返回 {@code true}；格式异常 / 不匹配一律 {@code false}（常量时间比较）
	 */
	public static boolean matches(String rawPassword, String storedHash) {
		if (rawPassword == null || storedHash == null || !storedHash.startsWith(PREFIX)) {
			return false;
		}
		String[] parts = storedHash.substring(PREFIX.length()).split(":");
		if (parts.length != 3) {
			return false;
		}
		try {
			int iterations = Integer.parseInt(parts[0]);
			byte[] salt = Base64.getDecoder().decode(parts[1]);
			byte[] expected = Base64.getDecoder().decode(parts[2]);
			PBEKeySpec spec = new PBEKeySpec(
					rawPassword.toCharArray(), salt, iterations, expected.length * Byte.SIZE);
			try {
				byte[] actual = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
				return MessageDigest.isEqual(expected, actual);
			}
			finally {
				spec.clearPassword();
			}
		}
		catch (IllegalArgumentException | GeneralSecurityException e) {
			return false;
		}
	}

	/**
	 * 生成与模组同格式的哈希（仅测试与本地工具用途；生产路径哈希在模组端生成）。
	 *
	 * @param rawPassword 密码原文
	 * @param iterations  PBKDF2 迭代次数
	 * @return {@code pbkdf2:<迭代>:<盐B64>:<哈希B64>} 格式的存储串
	 */
	public static String encodeForTests(String rawPassword, int iterations) {
		byte[] salt = new byte[16];
		new java.security.SecureRandom().nextBytes(salt);
		try {
			PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, 256);
			try {
				byte[] derived = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
				return PREFIX + iterations + ':' + Base64.getEncoder().encodeToString(salt)
						+ ':' + Base64.getEncoder().encodeToString(derived);
			}
			finally {
				spec.clearPassword();
			}
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable on this JDK", e);
		}
	}

	/**
	 * 常量时间字符串比较工具（测试与内部比对复用）。
	 *
	 * @param left  左值
	 * @param right 右值
	 * @return 逐字节常量时间比较结果
	 */
	public static boolean constantTimeEquals(String left, String right) {
		if (left == null || right == null) {
			return false;
		}
		return MessageDigest.isEqual(
				left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
	}
}
