package com.ae2webmanagement.ae2webmanagementmod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import com.ae2webmanagement.ae2webmanagementmod.access.AccessRegistration;
import com.ae2webmanagement.ae2webmanagementmod.access.client.AccessScreen;
import com.ae2webmanagement.ae2webmanagementmod.client.icon.IconService;
import com.ae2webmanagement.ae2webmanagementmod.icon.IconExporter;
import com.ae2webmanagement.ae2webmanagementmod.icon.IconPaths;

@Mod(value = AE2WebManagement.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AE2WebManagement.MODID, value = Dist.CLIENT)
public class AE2WebManagementClient {
    /** 客户端图标导出服务（专用服务器不加载本类，因此不会初始化）。 */
    private static IconService iconService;

    /**
     * 仅客户端入口：注册模组配置界面的工厂，并把图标渲染挂到每帧渲染完成事件上。
     */
    public AE2WebManagementClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // 图标渲染必须发生在渲染线程且游戏帧已渲染完成（纹理/模型均已就绪）：
        // NeoForge 1.21.1 的挂点是 RenderFrameEvent.Post（RenderTickEvent 已不存在）。
        NeoForge.EVENT_BUS.addListener(AE2WebManagementClient::onRenderFramePost);
    }

    /** 客户端生命周期日志，便于确认客户端侧初始化已执行。 */
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AE2WebManagement.LOGGER.info("HELLO FROM CLIENT SETUP");
        AE2WebManagement.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        iconService = new IconService();
        IconExporter.Global.set(iconService);
        AE2WebManagement.LOGGER.info("[AE2Web] Item icon export service ready (dir={})", IconPaths.root());
    }

    /** 注册 GUI 屏幕（NeoForge 1.21.1 用 RegisterMenuScreensEvent 而非 MenuScreens.register）。 */
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(AccessRegistration.ACCESS_MENU.get(), AccessScreen::new);
    }

    /** 每帧渲染完成后消费图标队列（每帧最多 {@code iconRenderPerFrame} 个）。 */
    static void onRenderFramePost(RenderFrameEvent.Post event) {
        IconService service = iconService;
        if (service != null) {
            service.renderTick();
        }
    }
}
