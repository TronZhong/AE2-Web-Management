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
 * C2S：接入方块 GUI 提交「注册网格」（roadmap §11 账号制）。
 *
 * <p>取代旧的按钮点击注册：注册同时携带显示名与网格密码（都可空 = 默认名 +
 * 公开网格）。服务端校验「玩家已有账号 + 控制器锚点存在 + 网格未注册」后
 * 创建身份记录并记 {@code ownerUuid}；重复注册（他人抢先）返回
 * {@code STATUS_ALREADY_EXISTS}，由每 tick 状态刷新把客户端切到只读视图。</p>
 *
 * <p>服务端处理器由 NeoForge 默认调度到主线程执行，并校验玩家当前打开的
 * 菜单与 payload 携带的 containerId 一致。</p>
 *
 * @param containerId 发起 GUI 的容器 ID
 * @param displayName 显示名；空白由服务端回退默认 {@code Grid @ x,y,z}
 * @param password    网格密码原文；空串 = 公开网格
 */
public record GridRegisterPayload(int containerId, String displayName, String password)
        implements CustomPacketPayload {

    public static final Type<GridRegisterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2WebManagement.MODID, "register_grid"));

    public static final StreamCodec<ByteBuf, GridRegisterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GridRegisterPayload::containerId,
            ByteBufCodecs.STRING_UTF8, GridRegisterPayload::displayName,
            ByteBufCodecs.STRING_UTF8, GridRegisterPayload::password,
            GridRegisterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理器（主线程）：仅当玩家正打开匹配的 {@link AccessMenu} 且菜单
     * 持有服务端方块实体时执行注册。
     *
     * @param payload 客户端提交的注册表单
     * @param context 载荷上下文（玩家为 {@link ServerPlayer}）
     */
    public static void handleOnServer(GridRegisterPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AccessMenu menu) || menu.containerId != payload.containerId()) {
            return;
        }
        menu.handleRegister(payload.displayName(), payload.password());
    }
}
