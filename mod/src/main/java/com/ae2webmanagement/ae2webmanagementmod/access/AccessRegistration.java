package com.ae2webmanagement.ae2webmanagementmod.access;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;

import com.ae2webmanagement.ae2webmanagementmod.AE2WebManagement;

/**
 * 「接入」方块相关的注册：方块、物品、方块实体类型、AE2 capability、及自有创造标签页。
 *
 * <p>全部注册都集中在此类中，通过 {@link #register(IEventBus)} 和 {@code @SubscribeEvent}
 * 方法接入模组的事件总线。方块实体的 AE2 网格能力通过 {@link AECapabilities#IN_WORLD_GRID_NODE_HOST}
 * 注册给 NeoForge，供 AE2 {@link appeng.api.networking.GridHelper#getNodeHost} 发现。
 */
public final class AccessRegistration {

    // ------------------------------ 注册器 ------------------------------

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AE2WebManagement.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AE2WebManagement.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AE2WebManagement.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AE2WebManagement.MODID);

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

    /**
     * 注册方块、物品、方块实体、创造标签页。由 {@link AE2WebManagement} 构造器调用。
     */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    // ------------------------------ AE2 网格 capability ------------------------------

    /**
     * 向 AE2 暴露 {@link IInWorldGridNodeHost} 能力，让 AE2 的 {@code GridHelper.getNodeHost}
     * 能发现本方块实体为网格节点宿主。
     *
     * <p>注册方法为 {@link RegisterCapabilitiesEvent#registerBlockEntity registerBlockEntity}。
     * 上下文类型为 {@code Void}（参见 {@link AECapabilities#IN_WORLD_GRID_NODE_HOST} 的声明）。
     */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ACCESS_BLOCK_ENTITY.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);
    }
}