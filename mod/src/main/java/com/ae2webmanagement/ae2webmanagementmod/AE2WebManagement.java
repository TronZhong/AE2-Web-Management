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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.ae2webmanagement.ae2webmanagementmod.access.AccessRegistration;
import com.ae2webmanagement.ae2webmanagementmod.command.CommandManager;
import com.ae2webmanagement.ae2webmanagementmod.http.LocalDataServer;
import com.ae2webmanagement.ae2webmanagementmod.snapshot.SnapshotManager;

/**
 * 模组入口。这里只负责配置、注册接入方块，以及服务端启停事件钩子。
 *
 * <p>方块注册与 AE2 capability 配置在 {@link AccessRegistration}；
 * 快照循环由 {@link SnapshotManager} 在主线程周期执行，合成命令由
 * {@link CommandManager} 在主线程持久化并提交。
 */
@Mod(AE2WebManagement.MODID)
public class AE2WebManagement {
    /** 必须与 neoforge.mods.toml 中的 {@code modId} 一致。 */
    public static final String MODID = "ae2webmanagement";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final SnapshotManager SNAPSHOT_MANAGER = new SnapshotManager();
    private static final CommandManager COMMAND_MANAGER = new CommandManager();
    private static final LocalDataServer LOCAL_DATA_SERVER =
            new LocalDataServer(SNAPSHOT_MANAGER, COMMAND_MANAGER);

    /** 供后续本地 HTTP 层读取最近快照。 */
    public static SnapshotManager snapshotManager() {
        return SNAPSHOT_MANAGER;
    }

    public AE2WebManagement(IEventBus modEventBus, ModContainer modContainer) {
        // ---- 接入方块（方块/物品/方块实体/capability/创造模式标签页） ----
        AccessRegistration.register(modEventBus);
        modEventBus.register(AccessRegistration.class);

        // ---- 运行时配置 ----
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // ---- 服务端生命周期事件 ----
        NeoForge.EVENT_BUS.register(this);
    }

    /** 服务端启动时恢复身份、快照与命令缓存。 */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("AE2 Web Management data pipeline starting (dedicated={})",
                event.getServer().isDedicatedServer());
        SNAPSHOT_MANAGER.start(event.getServer());
        COMMAND_MANAGER.start(event.getServer());
        LOCAL_DATA_SERVER.start();
    }

    /** 服务端主线程每 tick 调用一次；内部按配置的秒数限频。 */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        SNAPSHOT_MANAGER.tick(event.getServer());
        COMMAND_MANAGER.tick(event.getServer());
    }

    /** 服务端停止时清理运行时缓存。 */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOCAL_DATA_SERVER.stop();
        COMMAND_MANAGER.stop();
        SNAPSHOT_MANAGER.stop();
        LOGGER.info("AE2 Web Management data pipeline stopping");
    }
}