package com.ae2webmanagement.ae2webmanagementmod.access;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 「接入」方块：放在 AE2 网络旁，作为网格节点加入网络并定位 ME 控制器。
 * 右击打开 GUI（{@link AccessScreen}）。
 */
public class AccessBlock extends BaseEntityBlock {

    public AccessBlock(Properties properties) {
        super(properties);
    }

    /** 声明方块保存/加载时使用哪个构造器重建。 */
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(AccessBlock::new);
    }

    /** 让方块本体按模型渲染；BaseEntityBlock 默认会返回 INVISIBLE。 */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 在指定坐标创建接入方块的方块实体。 */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AccessBlockEntity(pos, state);
    }

    /** 玩家空手右击时打开注册 GUI；服务端负责打开菜单，客户端返回成功表示已处理。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            var menuProvider = state.getMenuProvider(level, pos);
            if (menuProvider != null) {
                player.openMenu(menuProvider);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
