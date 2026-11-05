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
 * C2S：接入方块 GUI 提交新的显示名（displayName 改名，docs/roadmap.md §5）。
 *
 * <p>复用 {@link GridPasswordPayload} 的通道模式（字符串无法进 containerData，
 * 用自定义 payload 走 C2S）；服务端处理器由 NeoForge 默认调度到主线程，
 * 并校验玩家当前打开的菜单与 payload 携带的 containerId 一致，防止越权改名。</p>
 *
 * <p>改名只影响网页展示名（HTTP 网络列表的 {@code displayName} 字段），
 * 不改 accessId / token / 密码哈希；空串或超长由服务端忽略。</p>
 *
 * @param containerId 发起 GUI 的容器 ID
 * @param displayName 新的显示名（服务端会 trim 并校验长度）
 */
public record GridRenamePayload(int containerId, String displayName) implements CustomPacketPayload {

    public static final Type<GridRenamePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2WebManagement.MODID, "rename_grid"));

    public static final StreamCodec<ByteBuf, GridRenamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GridRenamePayload::containerId,
            ByteBufCodecs.STRING_UTF8, GridRenamePayload::displayName,
            GridRenamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理器（主线程）：仅当玩家正打开匹配的 {@link AccessMenu} 且菜单
     * 持有服务端方块实体时执行改名，并把最新身份推回客户端。
     *
     * @param payload 客户端提交的新显示名
     * @param context 载荷上下文（玩家为 {@link ServerPlayer}）
     */
    public static void handleOnServer(GridRenamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AccessMenu menu) || menu.containerId != payload.containerId()) {
            return;
        }
        menu.handleRename(payload.displayName());
    }
}
