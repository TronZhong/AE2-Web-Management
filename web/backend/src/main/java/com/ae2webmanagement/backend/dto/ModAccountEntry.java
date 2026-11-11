package com.ae2webmanagement.backend.dto;

/**
 * 模组账号记录（模组契约「账号 API」的单个条目，0.1.0 起）。
 *
 * <p>{@code passwordHash} 为模组 PBKDF2 哈希（{@code pbkdf2:<迭代>:<盐B64>:<哈希B64>}，
 * 与模组 {@code GridPasswords} 格式对齐）——哈希必须在同步通道中传输，后端在
 * 本地校验登录（明文不出模组）；通道仅本机回环 + 共享密钥可达。</p>
 *
 * @param playerUuid   绑定玩家的 UUID（唯一键；离线模式由名字派生，同样稳定）
 * @param playerName   玩家最新名（网页登录别名，随改名跟随更新）
 * @param passwordHash PBKDF2 哈希
 * @param createdAt    注册时间（epoch 毫秒）
 */
public record ModAccountEntry(String playerUuid, String playerName, String passwordHash, long createdAt) {
}
