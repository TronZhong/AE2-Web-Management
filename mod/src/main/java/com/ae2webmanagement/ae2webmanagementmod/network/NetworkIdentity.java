package com.ae2webmanagement.ae2webmanagementmod.network;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 一条「网络接入身份」，对应 docs/design.md §4.1。
 *
 * <p>以 ME 控制器为锚点（控制器维度 + 坐标）标识一个 AE2 网络，因为 AE2 网络本身没有持久 ID。
 * 这些记录保存在 Minecraft 的 {@code SavedData}（挂 Overworld）里，而非某个方块自己的 NBT。
 *
 * @param accessId        存档内专属 ID（由模组生成）
 * @param controllerLevel 锚点：ME 控制器所在维度
 * @param controllerPos   锚点：ME 控制器方块坐标
 * @param token           网页访问令牌（后端持有）
 * @param displayName     显示名
 * @param createdAt       建档时间（epoch 毫秒）
 * @param lastSeenAt      最近一次在线时间（epoch 毫秒）
 */
public record NetworkIdentity(
        UUID accessId,
        ResourceKey<Level> controllerLevel,
        BlockPos controllerPos,
        String token,
        String displayName,
        long createdAt,
        long lastSeenAt) {
}