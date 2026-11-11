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
import net.minecraft.server.level.ServerPlayer;
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
import com.ae2webmanagement.ae2webmanagementmod.account.AccountManager;
import com.ae2webmanagement.ae2webmanagementmod.data.AccountStore;
import com.ae2webmanagement.ae2webmanagementmod.data.GridPasswords;
import com.ae2webmanagement.ae2webmanagementmod.data.GridIdentityStore;
import com.ae2webmanagement.ae2webmanagementmod.grid.ControllerAnchor;
import com.ae2webmanagement.ae2webmanagementmod.grid.ControllerLocator;
import com.ae2webmanagement.ae2webmanagementmod.grid.GridIdentity;

/**
 * 「接入」方块的方块实体：一个 AE2 托管网格节点 + GUI 交互。
 *
 * <p>放在 AE2 网格旁 → 自动加入网格 → 定位 ME 控制器 → 右击打开 GUI →
 * GUI 按账号/所有权分层（roadmap §11）：注册网页账号 → 注册网格（记 owner）→
 * 本人网格管理（改名/改密/删除）、他人网格只读。</p>
 *
 * <p><b>服务端权限校验</b>：全部写操作（注册网格、改名、改密、删除）在主线程
 * 校验「账号存在 + {@code ServerPlayer#getUUID()} == ownerUuid + 网格存在」，
 * 不依赖客户端隐藏按钮；payload 处理前重查（一人删除网格时另一人的后续写
 * 操作由服务端拒绝，GUI 经每 tick 刷新自动回到最新状态）。</p>
 */
public class AccessBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost, IGridNodeListener<AccessBlockEntity>, MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 本方块实体对应的 AE2 托管网格节点。 */
    private final IManagedGridNode mainNode;

    /** 最近一次定位到的 ME 控制器锚点。 */
    private ControllerAnchor controllerAnchor;

    /** 最近一次「注册网格」操作的结果状态码（{@link AccessMenu#STATUS_NO_CONTROLLER} 等）。 */
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
     * GUI 会显示不可注册状态。</p>
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

    /**
     * 按控制器锚点查身份表，返回该网格的身份记录（未注册时为空）。
     *
     * @param anchor 控制器锚点
     * @return 对应的 {@link GridIdentity}；未注册 / 无法访问身份表时为空
     */
    public Optional<GridIdentity> getIdentity(ControllerAnchor anchor) {
        GridIdentityStore store = identityStoreOrNull(anchor);
        if (store == null) {
            return Optional.empty();
        }
        return store.findAccessId(anchor).flatMap(store::get);
    }

    // ===================== 账号制状态机（roadmap §11） =====================

    /**
     * GUI 视图状态机（主线程调用，{@link AccessMenu} 每 tick 读取）：
     * 无控制器 → 无账号（注册账号）→ 网格未注册（注册网格）→
     * 他人网格（只读）/ 本人网格（管理）。
     *
     * <p>无账号玩家打开已注册网格也一律先引导注册账号（不提供「免注册只读」）。</p>
     *
     * @param player 打开 GUI 的玩家
     * @return {@link AccessMenu} 的 {@code VIEW_*} 常量
     */
    public int resolveView(ServerPlayer player) {
        if (!(this.level instanceof ServerLevel sl) || this.controllerAnchor == null) {
            return AccessMenu.VIEW_NO_CONTROLLER;
        }
        GridIdentity identity = findIdentity().orElse(null);
        if (identity == null) {
            return hasAccount(player.getUUID())
                    ? AccessMenu.VIEW_REGISTER_GRID
                    : AccessMenu.VIEW_REGISTER_ACCOUNT;
        }
        return isOwnerOf(identity, player) ? AccessMenu.VIEW_MANAGE : AccessMenu.VIEW_READONLY;
    }

    /**
     * 玩家是否已注册网页账号（主线程调用）。
     *
     * @param playerUuid 玩家 UUID
     * @return 账号表已存在该玩家为 {@code true}
     */
    public boolean hasAccount(UUID playerUuid) {
        if (!(this.level instanceof ServerLevel sl)) {
            return false;
        }
        return AccountStore.get(sl).get(playerUuid).isPresent();
    }

    /**
     * 判定玩家是否为网格 owner（写操作的权限三要素之一）：
     * 身份记录了 ownerUuid 且与玩家 UUID 一致，且该玩家账号仍存在。
     *
     * @param identity 网格身份记录
     * @param player   操作玩家
     * @return 满足「账号存在 + 玩家 == owner」为 {@code true}
     */
    public boolean isOwnerOf(GridIdentity identity, ServerPlayer player) {
        if (identity.ownerUuid() == null || !identity.ownerUuid().equals(player.getUUID())) {
            return false;
        }
        return hasAccount(player.getUUID());
    }

    /**
     * 解析网格 owner 的玩家名（经账号表；HTTP 网格列表与 GUI 只读视图展示用）。
     *
     * @param identity 网格身份记录
     * @return owner 玩家名；无主网格或账号缺失为空串
     */
    public String ownerNameOf(GridIdentity identity) {
        if (identity.ownerUuid() == null || !(this.level instanceof ServerLevel sl)) {
            return "";
        }
        AccountManager accounts = AE2WebManagement.accountManager();
        String name = accounts.nameOf(identity.ownerUuid());
        return name == null ? "" : name;
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
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return null;
        }
        return new AccessMenu(containerId, playerInv, this, serverPlayer);
    }

    // ===================== 账号注册 =====================

    /**
     * 注册网页账号（{@link AccessMenu#handleAccountRegister} 转发，主线程）。
     *
     * <p>账号只能游戏内注册（roadmap §11）：已注册的玩家静默忽略（GUI 每 tick
     * 刷新会切走表单）；密码非法静默忽略。成功后立即刷新账号镜像，本地 API
     * 的 {@code GET /accounts} 无需等待下一周期。</p>
     *
     * @param player   注册玩家（playerUuid 为账号唯一键，playerName 为登录别名）
     * @param password 密码原文（1..64 字符）
     */
    public void registerAccount(ServerPlayer player, String password) {
        if (!(this.level instanceof ServerLevel sl)) {
            return;
        }
        if (!GridPasswords.isValidPassword(password)) {
            LOGGER.warn("[AE2Web] Ignored invalid account password (length out of 1..{}) at {}",
                    GridPasswords.MAX_PASSWORD_LENGTH, this.worldPosition);
            return;
        }
        AccountStore store = AccountStore.get(sl);
        if (store.get(player.getUUID()).isPresent()) {
            LOGGER.warn("[AE2Web] Ignored account register: player {} already has an account", player.getUUID());
            return;
        }
        store.register(player.getUUID(), player.getGameProfile().getName(), password)
                .ifPresent(entry -> {
                    AE2WebManagement.accountManager().put(entry);
                    LOGGER.info("[AE2Web] Registered web account for player {} ({})",
                            entry.playerName(), entry.playerUuid());
                });
    }

    // ===================== 注册网格 =====================

    /**
     * 「注册网格」提交（{@link AccessMenu#handleRegister} 转发，主线程）。
     *
     * <p>服务端权限校验：玩家必须已有网页账号；控制器锚点存在且网格未注册。
     * 注册成功记 {@code ownerUuid} 并立即刷新身份镜像缓存（网页列表无需等待
     * 采样周期）；网格已注册（含他人抢先）返回 {@code STATUS_ALREADY_EXISTS}。</p>
     *
     * @param player      注册玩家
     * @param displayName 显示名（空白回退默认 {@code Grid @ x,y,z}；超长忽略并用默认名）
     * @param password    网格密码原文；空串 = 公开网格
     */
    public void tryRegisterGrid(ServerPlayer player, String displayName, String password) {
        if (this.controllerAnchor == null) {
            this.registrationStatus = AccessMenu.STATUS_NO_CONTROLLER;
            return;
        }
        if (!(this.level instanceof ServerLevel sl)) {
            return;
        }

        GridIdentityStore store = GridIdentityStore.get(sl);
        if (!hasAccount(player.getUUID())) {
            LOGGER.warn("[AE2Web] Ignored grid register: player {} has no web account at {}",
                    player.getUUID(), this.worldPosition);
            this.registrationStatus = AccessMenu.STATUS_IDLE;
            return;
        }

        String name = validateDisplayName(displayName);
        String passwordHash = "";
        if (password != null && !password.isEmpty()) {
            if (!GridPasswords.isValidPassword(password)) {
                LOGGER.warn("[AE2Web] Ignored invalid grid password (length out of 1..{}) at {}",
                        GridPasswords.MAX_PASSWORD_LENGTH, this.worldPosition);
            } else {
                passwordHash = GridPasswords.hash(password);
            }
        }

        GridIdentityStore.Result result = store.register(this.controllerAnchor, player.getUUID(), name, passwordHash);
        this.registrationStatus = switch (result) {
            case GridIdentityStore.Created c -> {
                AE2WebManagement.snapshotManager().updateIdentity(c.identity());
                LOGGER.info("[AE2Web] Created grid identity: accessId={}, owner={}, anchor={} @ {}",
                        c.identity().accessId(), player.getGameProfile().getName(),
                        c.identity().controllerLevel().location(), c.identity().controllerPos());
                yield AccessMenu.STATUS_CREATED;
            }
            case GridIdentityStore.AlreadyExists e -> {
                LOGGER.info("[AE2Web] Grid already registered: accessId={}, anchor={} @ {}",
                        e.identity().accessId(),
                        e.identity().controllerLevel().location(), e.identity().controllerPos());
                yield AccessMenu.STATUS_ALREADY_EXISTS;
            }
        };
    }

    /**
     * 校验显示名：空白回退空串（由身份表回退默认名）；超长拒绝并回退默认名。
     */
    private String validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        String trimmed = displayName.trim();
        if (trimmed.length() > AccessMenu.MAX_DISPLAY_NAME_LENGTH) {
            LOGGER.warn("[AE2Web] Ignored too-long display name (max {}): {}",
                    AccessMenu.MAX_DISPLAY_NAME_LENGTH, this.worldPosition);
            return "";
        }
        return trimmed;
    }

    /** 最近一次注册操作的结果状态码，供 {@link AccessMenu} 同步到客户端。 */
    public int getLastRegistrationStatus() {
        return this.registrationStatus;
    }

    // ===================== 网格密码 =====================

    /**
     * 当前网格是否已设置网格密码（主线程调用）。
     *
     * @return 已设置且未清除为 {@code true}；未注册网格 / 无控制器锚点为 {@code false}
     */
    public boolean isPasswordSet() {
        return findIdentity()
                .map((identity) -> identity.passwordHash() != null && !identity.passwordHash().isEmpty())
                .orElse(false);
    }

    /**
     * 修改网格密码（{@link AccessMenu#handleChangePassword} 转发，主线程）。
     *
     * <p>服务端权限校验：仅 owner 可改；旧密码须与当前哈希匹配（未设密码时
     * 旧密码必须为空串）。新密码空串 = 清除（恢复公开网格）；非法输入静默忽略。
     * 成功后立即刷新身份镜像缓存（含密码哈希），HTTP 校验端点无需等待采样周期。</p>
     *
     * @param player      操作玩家（须为 owner）
     * @param oldPassword 旧密码原文
     * @param newPassword 新密码原文；空串 = 清除密码
     */
    public void changeGridPassword(ServerPlayer player, String oldPassword, String newPassword) {
        GridIdentityStore store = identityStoreOrNull();
        if (store == null) {
            return;
        }
        GridIdentity identity = findIdentity().orElse(null);
        if (identity == null) {
            LOGGER.warn("[AE2Web] Ignored password change: grid not registered at {}", this.worldPosition);
            return;
        }
        if (!isOwnerOf(identity, player)) {
            LOGGER.warn("[AE2Web] Denied password change: player {} is not the owner of grid {}",
                    player.getUUID(), identity.accessId());
            return;
        }

        String currentHash = identity.passwordHash() == null ? "" : identity.passwordHash();
        boolean oldMatches = currentHash.isEmpty()
                ? (oldPassword == null || oldPassword.isEmpty())
                : GridPasswords.verify(oldPassword, currentHash);
        if (!oldMatches) {
            LOGGER.warn("[AE2Web] Denied password change: old password mismatch for grid {}", identity.accessId());
            return;
        }

        boolean clear = newPassword == null || newPassword.isEmpty();
        if (!clear && !GridPasswords.isValidPassword(newPassword)) {
            LOGGER.warn("[AE2Web] Ignored invalid grid password (length out of 1..{}): {}",
                    GridPasswords.MAX_PASSWORD_LENGTH, this.worldPosition);
            return;
        }

        UUID accessId = identity.accessId();
        store.setPassword(accessId, clear ? null : newPassword).ifPresent((updated) -> {
            AE2WebManagement.snapshotManager().updateIdentity(updated);
            LOGGER.info("[AE2Web] Grid password {} for grid {}",
                    clear ? "cleared" : "updated", accessId);
        });
    }

    // ===================== 改名 =====================

    /**
     * 修改当前网格的显示名（{@link AccessMenu#handleRename} 转发，主线程）。
     *
     * <p>服务端权限校验：仅 owner 可改；须已注册、新名非空白、长度不超过
     * {@link AccessMenu#MAX_DISPLAY_NAME_LENGTH}。写入身份表 SavedData 后立即
     * 刷新 SnapshotManager 的身份镜像缓存，让网页网格列表无需等待采样周期。</p>
     *
     * @param player  操作玩家（须为 owner）
     * @param newName 新显示名原文
     * @return 是否成功改名（网格未注册 / 无权限 / 非法输入时为 {@code false}）
     */
    public boolean renameGrid(ServerPlayer player, String newName) {
        GridIdentityStore store = identityStoreOrNull();
        if (store == null) {
            return false;
        }
        GridIdentity identity = findIdentity().orElse(null);
        if (identity == null) {
            LOGGER.warn("[AE2Web] Ignored rename: grid not registered at {}", this.worldPosition);
            return false;
        }
        if (!isOwnerOf(identity, player)) {
            LOGGER.warn("[AE2Web] Denied rename: player {} is not the owner of grid {}",
                    player.getUUID(), identity.accessId());
            return false;
        }
        if (newName == null || newName.isBlank()) {
            LOGGER.warn("[AE2Web] Ignored rename: empty display name at {}", this.worldPosition);
            return false;
        }
        String trimmed = newName.trim();
        if (trimmed.length() > AccessMenu.MAX_DISPLAY_NAME_LENGTH) {
            LOGGER.warn("[AE2Web] Ignored rename: display name too long (max {}): {}",
                    AccessMenu.MAX_DISPLAY_NAME_LENGTH, this.worldPosition);
            return false;
        }

        var updated = store.rename(identity.accessId(), trimmed);
        updated.ifPresent((value) -> {
            AE2WebManagement.snapshotManager().updateIdentity(value);
            LOGGER.info("[AE2Web] Renamed grid {} to \"{}\"", identity.accessId(), value.displayName());
        });
        return updated.isPresent();
    }

    // ===================== 删除网格 =====================

    /**
     * 删除当前网格（{@link AccessMenu#handleDelete} 转发，主线程）。
     *
     * <p>服务端权限校验：仅 owner 可删。级联清理（roadmap §11）：身份记录 +
     * 快照（内存缓存与 {@code SnapshotStore} 落盘）+ {@code CommandStore} 该网格
     * 全部命令记录（含 PENDING，一并取消在途 AE2 计算）+ SnapshotManager 身份
     * 镜像。删除后接入方块回未注册态，可被任何有账号玩家重新注册。</p>
     *
     * @param player 操作玩家（须为 owner）
     * @return 是否成功删除（网格未注册 / 无权限为 {@code false}）
     */
    public boolean deleteGrid(ServerPlayer player) {
        if (!(this.level instanceof ServerLevel sl)) {
            return false;
        }
        GridIdentityStore store = GridIdentityStore.get(sl);
        GridIdentity identity = findIdentity().orElse(null);
        if (identity == null) {
            LOGGER.warn("[AE2Web] Ignored delete: grid not registered at {}", this.worldPosition);
            return false;
        }
        if (!isOwnerOf(identity, player)) {
            LOGGER.warn("[AE2Web] Denied delete: player {} is not the owner of grid {}",
                    player.getUUID(), identity.accessId());
            return false;
        }

        UUID accessId = identity.accessId();
        store.remove(accessId);
        AE2WebManagement.snapshotManager().removeGrid(accessId, sl);
        AE2WebManagement.commandManager().removeGridCommands(accessId, sl.getServer());
        this.registrationStatus = AccessMenu.STATUS_IDLE;
        LOGGER.info("[AE2Web] Deleted grid {} by owner {}", accessId, player.getGameProfile().getName());
        return true;
    }

    /**
     * @return Overworld 身份表（仅服务端主线程且已有控制器锚点时）；否则 {@code null}
     */
    private GridIdentityStore identityStoreOrNull() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.controllerAnchor == null) {
            return null;
        }
        return GridIdentityStore.get(serverLevel);
    }

    /**
     * 按显式锚点取 Overworld 身份表（仅服务端主线程且锚点非空时）；否则 {@code null}。
     *
     * @param anchor 控制器锚点
     * @return 身份表；无法访问时为 {@code null}
     */
    private GridIdentityStore identityStoreOrNull(ControllerAnchor anchor) {
        if (!(this.level instanceof ServerLevel serverLevel) || anchor == null) {
            return null;
        }
        return GridIdentityStore.get(serverLevel);
    }

    /**
     * 按当前控制器锚点查身份记录（主线程；未注册或无法访问时为空）。
     */
    private Optional<GridIdentity> findIdentity() {
        GridIdentityStore store = identityStoreOrNull();
        if (store == null) {
            return Optional.empty();
        }
        return store.findAccessId(this.controllerAnchor).flatMap(store::get);
    }

    // ===================== BlockEntity 生命周期 =====================

    /** 服务端方块实体加载后创建 AE2 托管节点，使其自动加入相邻网格。 */
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
