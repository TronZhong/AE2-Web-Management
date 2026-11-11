package com.ae2webmanagement.ae2webmanagementmod.access;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;

import com.ae2webmanagement.ae2webmanagementmod.AE2WebManagement;

/**
 * 「接入」方块相关的注册：方块/物品/方块实体/MenuType/创造标签页、AE2 capability。
 *
 * <p>全部注册集中在此类，通过 {@link #register(IEventBus)} 和 {@code @SubscribeEvent}
 * 方法接入模组事件总线。
 */
public final class AccessRegistration {

    // ------------------------------ 注册器 ------------------------------

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AE2WebManagement.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AE2WebManagement.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AE2WebManagement.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AE2WebManagement.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AE2WebManagement.MODID);

    // ------------------------------ 方块 / 物品 ------------------------------

    public static final DeferredBlock<AccessBlock> ACCESS_BLOCK = BLOCKS.register(
            "access_block",
            () -> new AccessBlock(BlockBehaviour.Properties.of().strength(2.0f, 6.0f)));

    public static final DeferredItem<BlockItem> ACCESS_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("access_block", ACCESS_BLOCK);

    // ------------------------------ 方块实体类型 ------------------------------

    public static final Supplier<BlockEntityType<AccessBlockEntity>> ACCESS_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "access_block",
            () -> BlockEntityType.Builder.of(AccessBlockEntity::new, ACCESS_BLOCK.get()).build(null));

    // ------------------------------ 容器菜单 ------------------------------

    public static final Supplier<MenuType<AccessMenu>> ACCESS_MENU = MENUS.register(
            "access_menu",
            () -> new MenuType<>(AccessMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // ------------------------------ 创造标签页 ------------------------------

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_MODE_TABS.register(
            "tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2webmanagement"))
                    .icon(() -> ACCESS_BLOCK_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ACCESS_BLOCK_ITEM.get());
                    })
                    .build());

    private AccessRegistration() {
    }

    // ------------------------------ 注册入口 ------------------------------

    /** 由 {@link AE2WebManagement} 构造器调用。 */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MENUS.register(modEventBus);
    }

    // ------------------------------ AE2 网格 capability ------------------------------

    /** 把接入方块实体暴露成 AE2 的世界内网格节点宿主。 */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ACCESS_BLOCK_ENTITY.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);
    }

    // ------------------------------ 网络协议（C2S/S2C payload） ------------------------------

    /**
     * 注册自定义网络包（roadmap §11 账号制）：C2S——账号注册、注册网格（名称+密码）、
     * 提交新显示名、修改网格密码（旧+新）、删除网格；S2C——服务端推送身份信息
     * （accessId/displayName/控制器位置/owner 标记）。NeoForge 默认把处理器调度到
     * 主线程，满足 {@code SavedData} 写入的线程要求。
     */
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                AccountRegisterPayload.TYPE,
                AccountRegisterPayload.STREAM_CODEC,
                AccountRegisterPayload::handleOnServer);
        registrar.playToServer(
                GridRegisterPayload.TYPE,
                GridRegisterPayload.STREAM_CODEC,
                GridRegisterPayload::handleOnServer);
        registrar.playToServer(
                GridRenamePayload.TYPE,
                GridRenamePayload.STREAM_CODEC,
                GridRenamePayload::handleOnServer);
        registrar.playToServer(
                GridChangePasswordPayload.TYPE,
                GridChangePasswordPayload.STREAM_CODEC,
                GridChangePasswordPayload::handleOnServer);
        registrar.playToServer(
                GridDeletePayload.TYPE,
                GridDeletePayload.STREAM_CODEC,
                GridDeletePayload::handleOnServer);
        registrar.playToClient(
                GridIdentityPayload.TYPE,
                GridIdentityPayload.STREAM_CODEC,
                GridIdentityPayload::handleOnClient);
    }
}