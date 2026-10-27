package com.ae2webmanagement.ae2webmanagementmod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
// 这个类不会在专用服务器上加载。在这里访问客户端代码是安全的。
@Mod(value = AE2WebManagement.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
// 你可以用 @EventBusSubscriber 自动注册类中所有带有 @SubscribeEvent 注解的静态方法
@EventBusSubscriber(modid = AE2WebManagement.MODID, value = Dist.CLIENT)
public class AE2WebManagementClient {
    public AE2WebManagementClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // 让 NeoForge 能为该模组的配置创建一个配置界面。
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // 进入配置界面的路径：模组(Mods)界面 > 点击你的模组 > 点击“配置”。
        // Do not forget to add translations for your config options to the en_us.json file.
        // 别忘了在 en_us.json 文件里为你的配置选项添加对应的翻译。
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        // 一些客户端初始化代码
        AE2WebManagement.LOGGER.info("HELLO FROM CLIENT SETUP");
        AE2WebManagement.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}