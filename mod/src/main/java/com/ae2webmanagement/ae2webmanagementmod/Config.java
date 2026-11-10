package com.ae2webmanagement.ae2webmanagementmod;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组运行时配置。对应 docs/design.md 里的本地数据口与快照参数。
 *
 * <p>这里只定义配置项；业务代码通过 {@code Config.XXX.get()} 读取。
 * 读取时机：启动/主线程时读取一次并缓存，避免在 HTTP/定时线程上直接触碰配置对象。
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 本地数据口的监听地址。后端与模组同机部署，默认保持 127.0.0.1，勿对外暴露。 */
    public static final ModConfigSpec.ConfigValue<String> HTTP_BIND_ADDRESS = BUILDER
            .comment("Bind address of the local data API. Keep as 127.0.0.1 unless you know what you are doing.")
            .define("httpBindAddress", "127.0.0.1");

    /** 本地数据口的 TCP 端口。 */
    public static final ModConfigSpec.IntValue HTTP_PORT = BUILDER
            .comment("TCP port of the local data API.")
            .defineInRange("httpPort", 8090, 1, 65535);

    /** 与后端约定的共享密钥（请求头 X-Shared-Secret）。留空 = 关闭校验（仅限开发）。 */
    public static final ModConfigSpec.ConfigValue<String> SHARED_SECRET = BUILDER
            .comment("Shared secret checked against the backend's X-Shared-Secret header. Empty disables the check (dev only).")
            .define("sharedSecret", "");

    /** 快照刷新间隔（真实时间秒）。默认 4 秒。 */
    public static final ModConfigSpec.IntValue SNAPSHOT_INTERVAL_SECONDS = BUILDER
            .comment("How often (wall-clock seconds) the inventory/crafting snapshot is refreshed.")
            .defineInRange("snapshotIntervalSeconds", 4, 1, 3600);

    /**
     * 是否导出物品图标（客户端离屏渲染为 16×16 PNG，经本地 API {@code /icons} 提供）。
     * 专用服务器无客户端渲染，此开关对服务端无影响；弱机器可关闭以省去渲染开销。
     */
    public static final ModConfigSpec.BooleanValue ICON_EXPORT_ENABLED = BUILDER
            .comment("Export item icons by off-screen rendering on the client. Disable on dedicated servers or weak machines.")
            .define("iconExportEnabled", true);

    /** 每帧最多渲染的图标数量（作业队列渐进补齐，数值越大补齐越快、单帧开销越大）。 */
    public static final ModConfigSpec.IntValue ICON_RENDER_PER_FRAME = BUILDER
            .comment("Maximum number of item icons rendered per frame.")
            .defineInRange("iconRenderPerFrame", 2, 1, 64);

    /**
     * 每个网格保留的终态（SUBMITTED/FAILED）合成命令历史上限。
     * 超出后按 submittedAt 从旧到新自动删除；PENDING 命令永不自动清理。
     */
    public static final ModConfigSpec.IntValue COMMAND_HISTORY_LIMIT = BUILDER
            .comment("Maximum number of finished (SUBMITTED/FAILED) crafting commands kept per grid.")
            .defineInRange("commandHistoryLimit", 50, 1, 1000);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}