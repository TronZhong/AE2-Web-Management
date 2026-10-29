package com.ae2webmanagement.ae2webmanagementmod.access;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 「接入」方块：放在 AE2 网络旁，作为网格节点加入网络，并定位该网络的 ME 控制器。
 *
 * <p>方块本身无复杂状态，具体逻辑都在 {@link AccessBlockInstance} 对应的方块实体
 * {@link AccessBlockEntity} 中。
 */
public class AccessBlock extends BaseEntityBlock {

    public AccessBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(AccessBlock::new);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AccessBlockEntity(pos, state);
    }
}