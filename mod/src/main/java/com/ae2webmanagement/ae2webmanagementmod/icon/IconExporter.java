package com.ae2webmanagement.ae2webmanagementmod.icon;

import net.minecraft.nbt.CompoundTag;

/**
 * 图标导出网关（模组公共侧）。
 *
 * <p>客户端 {@code IconService} 在客户端 setup 时通过 {@link Global#set} 注册实现，
 * 负责把 AEKey 的通用 NBT 入队、逐帧离屏渲染成 PNG；专用服务器无客户端，网关保持
 * no-op（{@link #NONE}），本地 API 的图标惰性补齐请求零开销（docs/roadmap.md §5）。</p>
 *
 * <p>本接口只依赖公共 NBT，HTTP 线程（{@code LocalDataServer}）可安全调用；
 * Minecraft/AE2 客户端状态一律由实现方在渲染线程处理。</p>
 */
public interface IconExporter {

    /**
     * 请求后台渲染一个 AEKey（其通用 NBT 表示，形如 {@code {"#t":"ae2:i","id":"minecraft:iron_block"}}）。
     *
     * <p>任意线程可调用；实现内部做去重、磁盘存在性检查后入队，渲染逐帧消费。</p>
     *
     * @param keyNbt AEKey 通用 NBT（调用方不应复用后修改，实现内部会复制）
     */
    void request(CompoundTag keyNbt);

    /** 无客户端（专用服务器）时的 no-op 实现。 */
    IconExporter NONE = keyNbt -> { };

    /** 全局网关：客户端注册实现，服务端保持 {@link #NONE}。 */
    final class Global {
        private static volatile IconExporter current = NONE;

        private Global() {
        }

        /** @return 当前生效的图标导出网关 */
        public static IconExporter get() {
            return current;
        }

        /** 客户端在 setup 时调用；传 null 会重置为 no-op。 */
        public static void set(IconExporter exporter) {
            current = exporter != null ? exporter : NONE;
        }
    }
}
