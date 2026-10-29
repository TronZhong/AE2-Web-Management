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

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}