package com.ae2webmanagement.ae2webmanagementmod.access;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.ae2webmanagement.ae2webmanagementmod.AE2WebManagement;

/**
 * S2C：接入方块 GUI 展示的网格身份信息（accessId / displayName / 控制器位置 /
 * owner 信息与当前玩家的账号标记，roadmap §11 账号制扩展）。
 *
 * <p>字符串无法走 containerData 的 int 通道，故用自定义 payload（与
 * {@link GridRegisterPayload} 同一模式，方向相反）：服务端在菜单打开、注册、
 * 改名、删除或网格变化后把最新身份推给客户端，客户端存入菜单供
 * {@code AccessScreen} 渲染。对应 roadmap 已完成项（GUI 迭代）+ §11 账号制。</p>
 *
 * <p>未注册 / 无控制器时对应字段为空串，{@code pos} 为 {@code BlockPos.ZERO}，
 * 客户端据此显示占位文案。</p>
 *
 * @param containerId 发起 GUI 的容器 ID（客户端据此匹配当前打开的菜单）
 * @param accessId    网格接入 ID；未注册为空串
 * @param displayName 显示名；未注册为空串
 * @param dimension   控制器所在维度；无控制器为空串
 * @param pos         控制器方块坐标；无控制器为 {@code BlockPos.ZERO}
 * @param hasAccount  当前打开 GUI 的玩家是否已注册网页账号（§11）
 * @param isOwner     当前玩家是否为本网格 owner（未注册网格恒 false，§11）
 * @param ownerName   网格 owner 的玩家名（经账号表解析；无主/账号缺失为空串）
 */
public record GridIdentityPayload(
        int containerId,
        String accessId,
        String displayName,
        String dimension,
        BlockPos pos,
        boolean hasAccount,
        boolean isOwner,
        String ownerName) implements CustomPacketPayload {

    public static final Type<GridIdentityPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2WebManagement.MODID, "grid_identity"));

    /**
     * 手写编解码器：字段数超过 {@code StreamCodec.composite} 的 6 参上限，
     * 改用 {@link StreamCodec#ofMember} 按 {@link #write(ByteBuf)} / {@link #read(ByteBuf)} 顺序编解码。
     */
    public static final StreamCodec<ByteBuf, GridIdentityPayload> STREAM_CODEC = StreamCodec.ofMember(
            GridIdentityPayload::write, GridIdentityPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 按 {@link #read(ByteBuf)} 相同的顺序写出全部字段。 */
    private void write(ByteBuf buf) {
        ByteBufCodecs.VAR_INT.encode(buf, this.containerId);
        ByteBufCodecs.STRING_UTF8.encode(buf, this.accessId);
        ByteBufCodecs.STRING_UTF8.encode(buf, this.displayName);
        ByteBufCodecs.STRING_UTF8.encode(buf, this.dimension);
        BlockPos.STREAM_CODEC.encode(buf, this.pos);
        ByteBufCodecs.BOOL.encode(buf, this.hasAccount);
        ByteBufCodecs.BOOL.encode(buf, this.isOwner);
        ByteBufCodecs.STRING_UTF8.encode(buf, this.ownerName);
    }

    /** 按 {@link #write(ByteBuf)} 相同的顺序读回全部字段。 */
    private static GridIdentityPayload read(ByteBuf buf) {
        return new GridIdentityPayload(
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                BlockPos.STREAM_CODEC.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf));
    }

    /**
     * 客户端处理器：把服务端推送的身份信息写入玩家当前打开的接入菜单
     * （containerId 匹配才生效），{@code AccessScreen} 每帧读取渲染。
     *
     * @param payload 服务端推送的身份信息
     * @param context 载荷上下文（客户端 {@code player()} 为本地玩家）
     */
    public static void handleOnClient(GridIdentityPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player != null && player.containerMenu instanceof AccessMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setClientIdentity(payload);
        }
    }
}
