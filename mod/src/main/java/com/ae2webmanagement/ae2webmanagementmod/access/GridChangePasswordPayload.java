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
 * C2S：接入方块 GUI 提交「修改网格密码」（旧密码 + 新密码，roadmap §11）。
 *
 * <p>取代旧的 {@code GridPasswordPayload}（无条件设密/清密）：本人网格的管理
 * 视图改密须先验旧密码（未设密码时旧密码必须为空串），新密码为空串 = 清除
 * （恢复公开网格）。仅网格 owner 可调用，服务端做完整权限校验。</p>
 *
 * <p>服务端处理器由 NeoForge 默认调度到主线程执行，并校验玩家当前打开的
 * 菜单与 payload 携带的 containerId 一致。</p>
 *
 * @param containerId 发起 GUI 的容器 ID
 * @param oldPassword 旧密码原文（未设密码时必须为空串）
 * @param newPassword 新密码原文；空串 = 清除密码（恢复公开网格）
 */
public record GridChangePasswordPayload(int containerId, String oldPassword, String newPassword)
        implements CustomPacketPayload {

    public static final Type<GridChangePasswordPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2WebManagement.MODID, "change_grid_password"));

    public static final StreamCodec<ByteBuf, GridChangePasswordPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GridChangePasswordPayload::containerId,
            ByteBufCodecs.STRING_UTF8, GridChangePasswordPayload::oldPassword,
            ByteBufCodecs.STRING_UTF8, GridChangePasswordPayload::newPassword,
            GridChangePasswordPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理器（主线程）：仅当玩家正打开匹配的 {@link AccessMenu} 且菜单
     * 持有服务端方块实体时执行改密。
     *
     * @param payload 客户端提交的旧/新密码
     * @param context 载荷上下文（玩家为 {@link ServerPlayer}）
     */
    public static void handleOnServer(GridChangePasswordPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AccessMenu menu) || menu.containerId != payload.containerId()) {
            return;
        }
        menu.handleChangePassword(payload.oldPassword(), payload.newPassword());
    }
}
