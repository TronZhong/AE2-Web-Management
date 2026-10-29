package com.ae2webmanagement.ae2webmanagementmod.access;

import java.util.EnumSet;
import java.util.Optional;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;

import com.ae2webmanagement.ae2webmanagementmod.data.NetworkIdentityStore;
import com.ae2webmanagement.ae2webmanagementmod.grid.ControllerAnchor;
import com.ae2webmanagement.ae2webmanagementmod.grid.ControllerLocator;

/**
 * 「接入」方块的方块实体：一个 AE2 托管网格节点 + GUI 交互。
 *
 * <p>放在 AE2 网络旁 → 自动加入网络 → 定位 ME 控制器 → 右击打开 GUI →
 * 点击「Register」在{@link NetworkIdentityStore}（SavedData）中登记或查重。
 */
public class AccessBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost, IGridNodeListener<AccessBlockEntity>, MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String SAVED_DATA_KEY = "ae2web_identities";

    /** 本方块实体对应的 AE2 托管网格节点。 */
    private final IManagedGridNode mainNode;

    /** 最近一次定位到的 ME 控制器锚点。 */
    private ControllerAnchor controllerAnchor;

    /** 最近一次「注册网络」操作的结果状态码（{@link AccessMenu#STATUS_NO_CONTROLLER} 等）。 */
    private int registrationStatus = AccessMenu.STATUS_NO_CONTROLLER;

    public AccessBlockEntity(BlockPos pos, BlockState blockState) {
        super(AccessRegistration.ACCESS_BLOCK_ENTITY.get(), pos, blockState);

        this.mainNode = GridHelper
                .createManagedNode(this, this)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setInWorldNode(true)
                .setIdlePowerUsage(1.0);
    }

    // ===================== IInWorldGridNodeHost =====================

    @Override
    public IGridNode getGridNode(Direction dir) {
        return this.mainNode.getNode();
    }

    // ===================== IGridNodeListener =====================

    @Override
    public void onSaveChanges(AccessBlockEntity owner, IGridNode node) {
        this.setChanged();
    }

    @Override
    public void onGridChanged(AccessBlockEntity owner, IGridNode node) {
        IGrid grid = node.getGrid();
        if (grid == null) {
            this.controllerAnchor = null;
            this.registrationStatus = AccessMenu.STATUS_NO_CONTROLLER;
            return;
        }

        this.controllerAnchor = ControllerLocator.locate(grid).orElse(null);

        if (this.controllerAnchor != null) {
            this.registrationStatus = AccessMenu.STATUS_IDLE;
            LOGGER.info("[AE2Web] Access block at {} located ME controller: dimension={}, pos={}",
                    this.worldPosition,
                    this.controllerAnchor.levelKey().location(),
                    this.controllerAnchor.pos());
        } else {
            this.registrationStatus = AccessMenu.STATUS_NO_CONTROLLER;
            LOGGER.warn("[AE2Web] Access block at {} joined a grid without an ME controller.",
                    this.worldPosition);
        }
    }

    /** 当前已定位到的控制器锚点。 */
    public Optional<ControllerAnchor> getControllerAnchor() {
        return Optional.ofNullable(this.controllerAnchor);
    }

    // ===================== MenuProvider → GUI =====================

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ae2webmanagement.access_block");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new AccessMenu(containerId, playerInv, this);
    }

    /**
     * GUI 按钮「Register」按下时由 {@link AccessMenu#clickMenuButton} 调用（已在主线程）。
     *
     * <p>从 Overworld 的 {@link NetworkIdentityStore}（SavedData）中按控制器锚点
     * 查重或创建新身份记录。
     */
    public void tryRegisterNetwork() {
        if (this.controllerAnchor == null) {
            this.registrationStatus = AccessMenu.STATUS_NO_CONTROLLER;
            return;
        }
        if (!(this.level instanceof ServerLevel sl)) {
            return;
        }

        NetworkIdentityStore store = sl.getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(NetworkIdentityStore::new, NetworkIdentityStore::load),
                        SAVED_DATA_KEY);

        NetworkIdentityStore.Result result = store.addOrGet(this.controllerAnchor);
        this.registrationStatus = switch (result) {
            case NetworkIdentityStore.Created c -> {
                LOGGER.info("[AE2Web] Created network identity: accessId={}, token={}, anchor={} @ {}",
                        c.identity().accessId(), c.identity().token(),
                        c.identity().controllerLevel().location(), c.identity().controllerPos());
                yield AccessMenu.STATUS_CREATED;
            }
            case NetworkIdentityStore.AlreadyExists e -> {
                LOGGER.info("[AE2Web] Network already registered: accessId={}, anchor={} @ {}",
                        e.identity().accessId(),
                        e.identity().controllerLevel().location(), e.identity().controllerPos());
                yield AccessMenu.STATUS_ALREADY_EXISTS;
            }
        };
    }

    /** 最近一次注册操作的结果状态码，供 {@link AccessMenu} 同步到客户端。 */
    public int getLastRegistrationStatus() {
        return this.registrationStatus;
    }

    // ===================== BlockEntity 生命周期 =====================

    @Override
    public void onLoad() {
        super.onLoad();
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