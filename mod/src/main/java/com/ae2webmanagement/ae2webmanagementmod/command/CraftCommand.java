package com.ae2webmanagement.ae2webmanagementmod.command;

import java.util.UUID;

/**
 * 一条待办合成命令，对应 docs/design.md §4.3。
 *
 * <p>存的是「意图」（要合成什么、多少），不是预计算好的计划：上线提交前会用当时的库存重新模拟。
 * {@code target} 暂用字符串占位（序列化后的 {@code AEKey}），待接入 AE2 后确定序列化格式。
 *
 * @param id       命令 ID（用于向网页回传结果）
 * @param accessId 目标网络
 * @param target   要合成的物品种类（AEKey 序列化，暂定）
 * @param amount   数量
 */
public record CraftCommand(
        UUID id,
        UUID accessId,
        String target,
        long amount) {
}