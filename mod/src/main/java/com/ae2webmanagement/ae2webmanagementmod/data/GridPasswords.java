package com.ae2webmanagement.ae2webmanagementmod.data;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 网格密码哈希工具（纯 JDK，无新增依赖）。
 *
 * <p>算法：PBKDF2WithHmacSHA256，随机 16 字节盐，60k 迭代；存储格式
 * {@code pbkdf2:<迭代>:<盐B64>:<哈希B64>}，存入 {@link NetworkIdentity} 的
 * {@code passwordHash} 字段（SavedData）。明文密码只出现在游戏内 GUI 输入
 * 与 HTTP 校验请求体，任何持久化层只保存哈希。</p>
 *
 * <p>线程安全：{@link SecretKeyFactory} 每次调用新建；可被主线程（GUI 设置）
 * 与 ae2web-http 线程（校验）并发使用。</p>
 */
public final class GridPasswords {

    /** 存储格式前缀，便于未来升级算法时兼容旧哈希。 */
    private static final String PREFIX = "pbkdf2:";

    /** PBKDF2 迭代次数：本机校验延迟约几十毫秒，足以对抗离线爆破个人密码。 */
    private static final int ITERATIONS = 60_000;

    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** 密码长度上限（与 GUI EditBox maxLength 一致）。 */
    public static final int MAX_PASSWORD_LENGTH = 64;

    private static final SecureRandom RANDOM = new SecureRandom();

    private GridPasswords() {
    }

    /**
     * @param password 待检查的密码原文
     * @return 是否为可设置的密码：非空且不超过 {@value #MAX_PASSWORD_LENGTH} 字符
     */
    public static boolean isValidPassword(String password) {
        return password != null && !password.isEmpty() && password.length() <= MAX_PASSWORD_LENGTH;
    }

    /**
     * 计算密码哈希。调用方须先用 {@link #isValidPassword} 校验。
     *
     * @param password 密码原文
     * @return {@code pbkdf2:<迭代>:<盐B64>:<哈希B64>} 格式的存储串
     * @throws IllegalStateException PBKDF2 不可用（JDK 环境异常，视为软件故障）
     */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        try {
            byte[] derived = derive(password, salt, ITERATIONS);
            return PREFIX + ITERATIONS + ':' + Base64.getEncoder().encodeToString(salt)
                    + ':' + Base64.getEncoder().encodeToString(derived);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable on this JDK", exception);
        }
    }

    /**
     * 校验密码与存储哈希是否匹配。
     *
     * @param password 用户输入的密码原文
     * @param stored   存储哈希（{@link #hash} 的产物）；{@code null}/空表示未设密码
     * @return 匹配返回 {@code true}；未设密码或格式异常一律返回 {@code false}
     */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null || !stored.startsWith(PREFIX)) {
            return false;
        }
        String[] parts = stored.substring(PREFIX.length()).split(":");
        if (parts.length != 3) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            return false;
        }
    }

    /** 执行一次 PBKDF2 派生。 */
    private static byte[] derive(String password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
