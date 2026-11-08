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
 * S2C：接入方块 GUI 展示的网格身份信息（accessId / displayName / 控制器位置）。
 *
 * <p>字符串无法走 containerData 的 int 通道，故用自定义 payload（与
 * {@link GridPasswordPayload} 同一模式，方向相反）：服务端在菜单打开、
 * 注册、改名或网格变化后把最新身份推给客户端，客户端存入菜单供
 * {@code AccessScreen} 渲染。对应 roadmap 已完成项（GUI 迭代）：展示
 * accessId/displayName/控制器位置）。</p>
 *
 * <p>未注册 / 无控制器时对应字段为空串，{@code pos} 为 {@code BlockPos.ZERO}，
 * 客户端据此显示占位文案。</p>
 *
 * @param containerId 发起 GUI 的容器 ID（客户端据此匹配当前打开的菜单）
 * @param accessId    网格接入 ID；未注册为空串
 * @param displayName 显示名；未注册为空串
 * @param dimension   控制器所在维度；无控制器为空串
 * @param pos         控制器方块坐标；无控制器为 {@code BlockPos.ZERO}
 */
public record GridIdentityPayload(
        int containerId,
        String accessId,
        String displayName,
        String dimension,
        BlockPos pos) implements CustomPacketPayload {

    public static final Type<GridIdentityPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2WebManagement.MODID, "grid_identity"));

    public static final StreamCodec<ByteBuf, GridIdentityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GridIdentityPayload::containerId,
            ByteBufCodecs.STRING_UTF8, GridIdentityPayload::accessId,
            ByteBufCodecs.STRING_UTF8, GridIdentityPayload::displayName,
            ByteBufCodecs.STRING_UTF8, GridIdentityPayload::dimension,
            BlockPos.STREAM_CODEC, GridIdentityPayload::pos,
            GridIdentityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
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
