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
 * C2S：接入方块 GUI 提交**网页账号注册**（roadmap §11 账号制）。
 *
 * <p>账号只能在游戏内注册（网页端无注册口）：无账号玩家打开接入方块 GUI 时
 * 先看到账号注册表单。密码确认仅在客户端比对，本 payload 只携带一个密码；
 * 服务端校验密码合法性后写 {@code AccountStore}（PBKDF2 哈希），处理完成后
 * GUI 经每 tick 状态刷新原地切换到后续视图。</p>
 *
 * <p>服务端处理器由 NeoForge 默认调度到主线程执行，并校验玩家当前打开的
 * 菜单与 payload 携带的 containerId 一致。</p>
 *
 * @param containerId 发起 GUI 的容器 ID
 * @param password    账号密码原文（1..64 字符；非法输入服务端静默忽略）
 */
public record AccountRegisterPayload(int containerId, String password) implements CustomPacketPayload {

    public static final Type<AccountRegisterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2WebManagement.MODID, "register_account"));

    public static final StreamCodec<ByteBuf, AccountRegisterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AccountRegisterPayload::containerId,
            ByteBufCodecs.STRING_UTF8, AccountRegisterPayload::password,
            AccountRegisterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理器（主线程）：仅当玩家正打开匹配的 {@link AccessMenu} 且菜单
     * 持有服务端方块实体时执行注册。
     *
     * @param payload 客户端提交的账号密码
     * @param context 载荷上下文（玩家为 {@link ServerPlayer}）
     */
    public static void handleOnServer(AccountRegisterPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AccessMenu menu) || menu.containerId != payload.containerId()) {
            return;
        }
        menu.handleAccountRegister(payload.password());
    }
}
