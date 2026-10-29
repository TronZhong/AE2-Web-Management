package com.ae2webmanagement.ae2webmanagementmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import com.ae2webmanagement.ae2webmanagementmod.access.AccessRegistration;

/**
 * 模组入口。这里只负责配置、注册接入方块，以及服务端启停事件钩子。
 *
 * <p>具体方块的注册与 AE2 capability 配置在 {@link AccessRegistration}；
 * 具体数据管线（快照循环、命令队列、本地 HTTP 口）将在后续子包中实现，
 * 并在 {@link #onServerStarting(ServerStartingEvent)} / {@link #onServerStopping(ServerStoppingEvent)}
 * 中启动/停止。
 */
@Mod(AE2WebManagement.MODID)
public class AE2WebManagement {
    /** 必须与 neoforge.mods.toml 中的 {@code modId} 一致。 */
    public static final String MODID = "ae2webmanagement";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AE2WebManagement(IEventBus modEventBus, ModContainer modContainer) {
        // ---- 接入方块（方块/物品/方块实体/capability/创造标签页）----
        AccessRegistration.register(modEventBus);
        modEventBus.register(AccessRegistration.class);

        // ---- 运行时配置 ----
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // ---- 服务端生命周期事件 ----
        NeoForge.EVENT_BUS.register(this);
    }

    /** 服务器启动时触发——后续在此启动本地数据口与快照循环。 */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("AE2 Web Management data pipeline starting (dedicated={})",
                event.getServer().isDedicatedServer());
    }

    /** 服务器停止时触发——后续在此关闭本地数据口、停止快照循环。 */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("AE2 Web Management data pipeline stopping");
    }
}