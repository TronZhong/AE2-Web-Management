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
 * C2S：接入方块 GUI 提交「删除网格」（二次确认由客户端完成，roadmap §11）。
 *
 * <p>仅网格 owner 可删除；服务端主线程级联清理：身份记录 + 快照（内存与
 * {@code SnapshotStore} 落盘）+ 该网格全部命令记录（含 PENDING）+ 身份镜像。
 * 删除后接入方块回未注册态，任何有账号玩家可重新注册。</p>
 *
 * <p>服务端处理器由 NeoForge 默认调度到主线程执行，并校验玩家当前打开的
 * 菜单与 payload 携带的 containerId 一致。</p>
 *
 * @param containerId 发起 GUI 的容器 ID
 */
public record GridDeletePayload(int containerId) implements CustomPacketPayload {

    public static final Type<GridDeletePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2WebManagement.MODID, "delete_grid"));

    public static final StreamCodec<ByteBuf, GridDeletePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GridDeletePayload::containerId,
            GridDeletePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理器（主线程）：仅当玩家正打开匹配的 {@link AccessMenu} 且菜单
     * 持有服务端方块实体时执行删除。
     *
     * @param payload 删除请求
     * @param context 载荷上下文（玩家为 {@link ServerPlayer}）
     */
    public static void handleOnServer(GridDeletePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AccessMenu menu) || menu.containerId != payload.containerId()) {
            return;
        }
        menu.handleDelete();
    }
}
