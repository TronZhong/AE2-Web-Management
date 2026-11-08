package com.ae2webmanagement.ae2webmanagementmod.access;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.ae2webmanagement.ae2webmanagementmod.AE2WebManagement;

/**
 * C2S：接入方块 GUI 提交网络密码（{@code EditBox} 内容无法走 containerData 的
 * int 通道，故用自定义 payload（roadmap 已完成项：网格密码增强项））。
 *
 * <p>{@code password} 为空字符串表示「清除密码」；服务端处理器由 NeoForge
 * 默认调度到主线程执行（写 SavedData 的要求），并校验玩家当前打开的菜单
 * 与 payload 携带的 containerId 一致，防止越权改密。</p>
 *
 * @param containerId 发起 GUI 的容器 ID
 * @param password    密码原文；空串 = 清除
 */
public record GridPasswordPayload(int containerId, String password) implements CustomPacketPayload {

    public static final Type<GridPasswordPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2WebManagement.MODID, "set_grid_password"));

    public static final StreamCodec<ByteBuf, GridPasswordPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GridPasswordPayload::containerId,
            ByteBufCodecs.STRING_UTF8, GridPasswordPayload::password,
            GridPasswordPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理器（主线程）：仅当玩家正打开匹配的 {@link AccessMenu} 且菜单
     * 持有服务端方块实体时执行设密/清密。
     *
     * @param payload 客户端提交的密码
     * @param context 载荷上下文（玩家为 {@link ServerPlayer}）
     */
    public static void handleOnServer(GridPasswordPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AccessMenu menu) || menu.containerId != payload.containerId()) {
            return;
        }
        menu.handleSetPassword(payload.password());
    }
}
