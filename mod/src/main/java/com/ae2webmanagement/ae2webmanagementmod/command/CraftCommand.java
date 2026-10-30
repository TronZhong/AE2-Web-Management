package com.ae2webmanagement.ae2webmanagementmod.command;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * 一条待办合成命令，对应 docs/design.md §4.3。
 *
 * <p>存的是「意图」（要合成什么、多少），不是预先计算好的计划：上线提交前会用
 * 当时的库存重新模拟。{@code target} 保存 {@code AEKey#toTagGeneric} 生成的 NBT。
 *
 * @param id       命令 ID（用于向网页回传结果）
 * @param accessId 目标网络
 * @param target   要合成的物品种类（AEKey 通用 NBT）
 * @param amount   数量
 * @param state    队列状态
 * @param submittedAt 受理或提交到 AE2 的 epoch 毫秒时间
 * @param result   当前结果说明
 */
public record CraftCommand(
        UUID id,
        UUID accessId,
        CompoundTag target,
        long amount,
        CommandState state,
        long submittedAt,
        String result) {

    public CraftCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accessId, "accessId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(state, "state");
        target = target.copy();
        result = result == null ? "" : result;
    }

    public CraftCommand withState(CommandState newState, long timestamp, String newResult) {
        return new CraftCommand(id, accessId, target, amount, newState, timestamp, newResult);
    }
}