package com.ae2webmanagement.ae2webmanagementmod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.ae2webmanagement.ae2webmanagementmod.access.AccessRegistration;
import com.ae2webmanagement.ae2webmanagementmod.access.client.AccessScreen;

@Mod(value = AE2WebManagement.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AE2WebManagement.MODID, value = Dist.CLIENT)
public class AE2WebManagementClient {
    public AE2WebManagementClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AE2WebManagement.LOGGER.info("HELLO FROM CLIENT SETUP");
        AE2WebManagement.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    /** 注册 GUI 屏幕（NeoForge 1.21.1 用 RegisterMenuScreensEvent 而非 MenuScreens.register）。 */
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(AccessRegistration.ACCESS_MENU.get(), AccessScreen::new);
    }
}