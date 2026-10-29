package com.ae2webmanagement.ae2webmanagementmod.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * ME 控制器的位置锚点，用于在「没有持久 ID 的 AE2 网络」里唯一标识一个网络（docs/design.md 决策 D1）。
 *
 * <p>这是纯值对象（不可变），供定位逻辑返回、供身份表持久化复用。
 *
 * @param levelKey 控制器所在维度
 * @param pos      控制器方块坐标（不可变拷贝）
 */
public record ControllerAnchor(ResourceKey<Level> levelKey, BlockPos pos) {

    /**
     * 由服务端关卡与坐标构造锚点。
     *
     * @param level 控制器所在的服务端关卡
     * @param pos   坐标（会拷贝成不可变对象，避免外部改动）
     */
    public static ControllerAnchor of(ServerLevel level, BlockPos pos) {
        return new ControllerAnchor(level.dimension(), pos.immutable());
    }
}