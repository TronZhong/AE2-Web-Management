package com.ae2webmanagement.ae2webmanagementmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
// 此处的值必须与 META-INF/neoforge.mods.toml 文件中的某个条目一致
@Mod(AE2WebManagement.MODID)
public class AE2WebManagement {
    // Define mod id in a common place for everything to reference
    // 在统一的地方定义模组 ID，方便各处引用
    public static final String MODID = "ae2webmanagement";
    // Directly reference a slf4j logger
    // 直接引用一个 slf4j 日志记录器
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "ae2webmanagement" namespace
    // 创建一个“延迟注册器”来存放方块，它们都将注册到 "ae2webmanagement" 命名空间下
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "ae2webmanagement" namespace
    // 创建一个“延迟注册器”来存放物品，它们都将注册到 "ae2webmanagement" 命名空间下
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "ae2webmanagement" namespace
    // 创建一个“延迟注册器”来存放创造模式标签页，它们都将注册到 "ae2webmanagement" 命名空间下
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "ae2webmanagement:example_block", combining the namespace and path
    // 创建一个 id 为 "ae2webmanagement:example_block" 的新方块，该 id 由命名空间和路径组合而成
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "ae2webmanagement:example_block", combining the namespace and path
    // 创建一个 id 为 "ae2webmanagement:example_block" 的方块物品(BlockItem)，id 由命名空间和路径组合而成
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "ae2webmanagement:example_id", nutrition 1 and saturation 2
    // 创建一个 id 为 "ae2webmanagement:example_id" 的新食物物品，营养值 1、饱和度加成 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "ae2webmanagement:example_tab" for the example item, that is placed after the combat tab
    // 为示例物品创建一个 id 为 "ae2webmanagement:example_tab" 的创造标签页，位置在“战斗”标签页之后
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ae2webmanagement")) //The language key for the title of your CreativeModeTab
            // 你的创造模式标签页标题对应的语言 key
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get());// Add the example item to the tab. For your own tabs, this method is preferred over the event
                // 把示例物品添加进标签页。对于你自己的标签页，推荐使用这个“方法”而不是事件
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // 模组类的构造函数是模组被加载时最先运行的代码。
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    // FML 会识别 IEventBus、ModContainer 等参数类型，并自动把它们传入。
    public AE2WebManagement(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        // 注册 commonSetup 方法，用于模组加载
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        // 把方块的“延迟注册器”注册到模组事件总线上，使方块被注册
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        // 把物品的“延迟注册器”注册到模组事件总线上，使物品被注册
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        // 把标签页的“延迟注册器”注册到模组事件总线上，使标签页被注册
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // 把我们自己注册到服务器和其他游戏事件总线上，以便接收感兴趣的事件。
        // Note that this is necessary if and only if we want *this* class (AE2WebManagement) to respond directly to events.
        // 注意：只有当我们需要“这个类(AE2WebManagement)”自己直接响应事件时才需要这一行。
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        // 如果这个类里没有任何 @SubscribeEvent 注解的方法(比如下面的 onServerStarting())，就不要加这一行。
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        // 把物品注册到一个创造标签页中
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        // 注册我们模组的 ModConfigSpec，让 FML 帮我们创建并加载配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        // 一些通用初始化代码
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Add the example block item to the building blocks tab
    // 把示例方块物品添加到“建筑方块”标签页
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    // 你可以使用 @SubscribeEvent，让事件总线自动发现并调用这些方法
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        // 当服务器启动时执行一些操作
        LOGGER.info("HELLO from server starting");
    }
}