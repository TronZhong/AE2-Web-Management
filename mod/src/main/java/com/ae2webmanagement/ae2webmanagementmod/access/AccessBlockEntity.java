package com.ae2webmanagement.ae2webmanagementmod.access;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

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

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;

import com.ae2webmanagement.ae2webmanagementmod.AE2WebManagement;
import com.ae2webmanagement.ae2webmanagementmod.data.GridPasswords;
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

    /** 返回本方块在任意方向上暴露的 AE2 网格节点。 */
    @Override
    public IGridNode getGridNode(Direction dir) {
        return this.mainNode.getNode();
    }

    // ===================== IGridNodeListener =====================

    /** AE2 修改节点持久化数据时，同步标记方块实体需要保存。 */
    @Override
    public void onSaveChanges(AccessBlockEntity owner, IGridNode node) {
        this.setChanged();
    }

    /**
     * 接入方块所属 AE2 网格发生变化时重新定位控制器。
     *
     * <p>成功定位则缓存控制器锚点并进入可注册状态；没有控制器或网格断开时，
     * GUI 会显示不可注册状态。
     */
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

    /** 提供菜单标题；实际菜单由 {@link #createMenu} 创建。 */
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ae2webmanagement.access_block");
    }

    /** 服务端打开 GUI 时创建持有方块实体引用的菜单。 */
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

        NetworkIdentityStore store = NetworkIdentityStore.get(sl);

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

    // ===================== 网格密码 =====================

    /**
     * 当前网络是否已设置网格密码（主线程调用）。
     *
     * @return 已设置且未清除为 {@code true}；未注册网络 / 无控制器锚点为 {@code false}
     */
    public boolean isPasswordSet() {
        NetworkIdentityStore store = identityStoreOrNull();
        if (store == null) {
            return false;
        }
        return store.findAccessId(this.controllerAnchor)
                .flatMap(store::get)
                .map((identity) -> identity.passwordHash() != null && !identity.passwordHash().isEmpty())
                .orElse(false);
    }

    /**
     * 设置或清除网格密码（{@link AccessMenu#handleSetPassword} 转发，主线程）。
     *
     * <p>写入身份表 SavedData（只存 PBKDF2 哈希）并立即刷新 SnapshotManager 的
     * 密码镜像，让 HTTP 校验端点无需等待下一个采样周期。</p>
     *
     * @param password 密码原文；空串 = 清除密码（恢复公开）；非法输入静默忽略
     */
    public void setGridPassword(String password) {
        NetworkIdentityStore store = identityStoreOrNull();
        if (store == null) {
            return;
        }
        UUID accessId = store.findAccessId(this.controllerAnchor).orElse(null);
        if (accessId == null) {
            LOGGER.warn("[AE2Web] Ignored password change: network not registered at {}",
                    this.worldPosition);
            return;
        }

        boolean clear = password == null || password.isEmpty();
        if (!clear && !GridPasswords.isValidPassword(password)) {
            LOGGER.warn("[AE2Web] Ignored invalid grid password (length out of 1..{}): {}",
                    GridPasswords.MAX_PASSWORD_LENGTH, this.worldPosition);
            return;
        }

        var updated = store.setPassword(accessId, clear ? null : password);
        updated.ifPresent((identity) -> {
            AE2WebManagement.snapshotManager().updatePasswordHash(accessId, identity.passwordHash());
            LOGGER.info("[AE2Web] Grid password {} for network {}",
                    clear ? "cleared" : "updated", accessId);
        });
    }

    /**
     * @return Overworld 身份表（仅服务端主线程且已有控制器锚点时）；否则 {@code null}
     */
    private NetworkIdentityStore identityStoreOrNull() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.controllerAnchor == null) {
            return null;
        }
        return NetworkIdentityStore.get(serverLevel);
    }

    // ===================== BlockEntity 生命周期 =====================

    /** 服务端方块实体加载后创建 AE2 托管节点，使其自动加入相邻网络。 */
    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            this.mainNode.create(this.level, this.worldPosition);
        }
    }

    /** 区块卸载时销毁 AE2 网格节点，避免访问未加载世界。 */
    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.mainNode.destroy();
    }

    /** 方块实体被移除时也销毁 AE2 网格节点。 */
    @Override
    public void setRemoved() {
        super.setRemoved();
        this.mainNode.destroy();
    }

    /** 在方块实体 NBT 中保存 AE2 节点数据。 */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.mainNode.saveToNBT(tag);
    }

    /** 从方块实体 NBT 恢复 AE2 节点数据。 */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.mainNode.loadFromNBT(tag);
    }
}
