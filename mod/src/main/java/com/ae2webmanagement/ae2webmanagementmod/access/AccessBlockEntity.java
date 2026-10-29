package com.ae2webmanagement.ae2webmanagementmod.access;

import java.util.EnumSet;
import java.util.Optional;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;

import com.ae2webmanagement.ae2webmanagementmod.grid.ControllerAnchor;
import com.ae2webmanagement.ae2webmanagementmod.grid.ControllerLocator;

/**
 * 「接入」方块的方块实体：一个 AE2 托管网格节点。
 *
 * <p>放在某个 AE2 网络旁后，本方块通过网格节点自动加入该网络；加入后即通过 {@link ControllerLocator}
 * 定位到该网络的 ME 控制器，将结果暂存起来，供后续写入身份表（{@code SavedData}）和在线快照。
 *
 * <p>此方块实体实现了 AE2 节点的完整生命周期绑定，遵循官方模式：
 * <ul>
 *   <li>{@link #onLoad() onLoad} → {@link IManagedGridNode#create create}</li>
 *   <li>{@link #onChunkUnloaded() onChunkUnloaded} → {@link IManagedGridNode#destroy destroy}</li>
 *   <li>{@link #setRemoved() setRemoved} → {@link IManagedGridNode#destroy destroy}（方块被拆）</li>
 * </ul>
 *
 * <p>向 AE2 暴露的 capability（{@link IInWorldGridNodeHost}）由 {@link AccessRegistration#registerCapabilities}
 * 统一注册，不在此类里手动注册。
 */
public class AccessBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost, IGridNodeListener<AccessBlockEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 本方块实体对应的 AE2 托管网格节点（由 {@link GridHelper#createManagedNode} 创建）。
     */
    private final IManagedGridNode mainNode;

    /**
     * 最近一次定位到的 ME 控制器锚点。网络离线时置 null。
     */
    private ControllerAnchor controllerAnchor;

    public AccessBlockEntity(BlockPos pos, BlockState blockState) {
        super(AccessRegistration.ACCESS_BLOCK_ENTITY.get(), pos, blockState);

        this.mainNode = GridHelper
                .createManagedNode(this, this)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setInWorldNode(true)
                .setIdlePowerUsage(1.0);
    }

    // ================================================================
    // IInWorldGridNodeHost —— 向 AE2 暴露网格节点（每个面）
    // ================================================================

    @Override
    public IGridNode getGridNode(Direction dir) {
        return this.mainNode.getNode();
    }

    // ================================================================
    // IGridNodeListener —— 节点状态回调
    // ================================================================

    /**
     * 节点数据变更时标记区块需保存。
     *
     * <p>这是 {@link IGridNodeListener} 唯一的抽象方法，必须实现。
     */
    @Override
    public void onSaveChanges(AccessBlockEntity owner, IGridNode node) {
        this.setChanged();
    }

    /**
     * 节点加入或离开网格时触发。
     *
     * <p>加入网格时（{@code grid != null}）立即尝试定位 ME 控制器；离开时清除缓存。
     * 定位到控制器后会打出 INFO 日志（供检验用）。
     */
    @Override
    public void onGridChanged(AccessBlockEntity owner, IGridNode node) {
        IGrid grid = node.getGrid();
        if (grid == null) {
            this.controllerAnchor = null;
            return;
        }

        this.controllerAnchor = ControllerLocator.locate(grid).orElse(null);

        if (this.controllerAnchor != null) {
            LOGGER.info("[AE2Web] Access block at {} located ME controller: dimension={}, pos={}",
                    this.worldPosition,
                    this.controllerAnchor.levelKey().location(),
                    this.controllerAnchor.pos());
        } else {
            LOGGER.warn("[AE2Web] Access block at {} joined a grid without an ME controller.",
                    this.worldPosition);
        }
    }

    /**
     * 当前已定位到的控制器锚点。
     *
     * <p>仅当方块所在网络在线且包含 ME 控制器时才非空。后续快照循环与身份表会依赖此方法。
     *
     * @return 锚点（可能空）
     */
    public Optional<ControllerAnchor> getControllerAnchor() {
        return Optional.ofNullable(this.controllerAnchor);
    }

    // ================================================================
    // BlockEntity 生命周期 → 节点生命周期映射
    // ================================================================

    @Override
    public void onLoad() {
        super.onLoad();
        // 只服务端（客户端不创建网格节点）
        if (this.level != null && !this.level.isClientSide) {
            this.mainNode.create(this.level, this.worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.mainNode.destroy();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.mainNode.destroy();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.mainNode.saveToNBT(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.mainNode.loadFromNBT(tag);
    }
}