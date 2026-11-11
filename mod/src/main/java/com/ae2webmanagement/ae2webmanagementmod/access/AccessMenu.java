package com.ae2webmanagement.ae2webmanagementmod.access;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;

import com.ae2webmanagement.ae2webmanagementmod.grid.ControllerAnchor;

/**
 * 「接入」方块的容器菜单，持有服务端 ↔ 客户端共享的状态数据。
 *
 * <p>状态值由 {@code containerData} 同步（随 {@code broadcastChanges} 每 tick
 * 从方块实体刷新，网格变化后客户端无需重开菜单即可看到最新状态）：
 * <ul>
 *   <li>{@code containerData[0]} —— GUI 视图状态机（服务端判定）：
 *       {@link #VIEW_NO_CONTROLLER} / {@link #VIEW_REGISTER_ACCOUNT} /
 *       {@link #VIEW_REGISTER_GRID} / {@link #VIEW_READONLY} / {@link #VIEW_MANAGE}</li>
 *   <li>{@code containerData[1]} —— 注册反馈状态：
 *       {@link #STATUS_NO_CONTROLLER} / {@link #STATUS_IDLE} /
 *       {@link #STATUS_CREATED} / {@link #STATUS_ALREADY_EXISTS}</li>
 *   <li>{@code containerData[2]} —— 网格密码标记：1 = 已设密码，0 = 未设（公开）</li>
 *   <li>{@code containerData[3]} —— 改密反馈打包值 {@code (seq << 2) | code}：
 *       低 2 位为 {@link #PASSWORD_CHANGE_IDLE} / {@link #PASSWORD_CHANGE_SUCCESS} /
 *       {@link #PASSWORD_CHANGE_OLD_MISMATCH} / {@link #PASSWORD_CHANGE_INVALID}，
 *       高位为随每次改密尝试递增的序号（containerData 只同步变化值，序号保证
 *       「连续两次改密都成功」也能被客户端检测到边沿）</li>
 * </ul>
 *
 * <p>roadmap §11 账号制权限分层：无账号玩家先注册账号；未注册网格由有账号玩家
 * 注册（注册成功记 owner）；他人网格只读（服务端拒绝一切写操作，不依赖客户端
 * 隐藏按钮）；本人网格可改名 / 改密 / 删除。字符串与布尔细节（accessId/
 * displayName/ownerName/hasAccount/isOwner）经 {@link GridIdentityPayload} 推给
 * 客户端展示。</p>
 */
public class AccessMenu extends AbstractContainerMenu {

    // ==================== 视图状态机（containerData[0]，服务端判定） ====================

    /** 视图：无控制器（现状保留，仅提示）。 */
    public static final int VIEW_NO_CONTROLLER = 0;
    /** 视图：玩家无网页账号 → 账号注册表单（密码 + 确认）。 */
    public static final int VIEW_REGISTER_ACCOUNT = 1;
    /** 视图：有账号但网格未注册 → 名称 + 密码注册表单。 */
    public static final int VIEW_REGISTER_GRID = 2;
    /** 视图：他人注册的网格 → 只读（服务端拒绝写操作）。 */
    public static final int VIEW_READONLY = 3;
    /** 视图：本人网格 → 改名 / 改密（旧 + 新）/ 删除（二次确认）。 */
    public static final int VIEW_MANAGE = 4;

    // ==================== 注册反馈状态（containerData[1]） ====================

    public static final int STATUS_NO_CONTROLLER = 0;
    public static final int STATUS_IDLE = 1;
    public static final int STATUS_CREATED = 2;
    public static final int STATUS_ALREADY_EXISTS = 3;

    // ==================== 改密反馈状态（containerData[3] 低 2 位） ====================

    /** 改密反馈：无（初始 / 本次尝试未产生有效结果）。 */
    public static final int PASSWORD_CHANGE_IDLE = 0;
    /** 改密反馈：修改成功（新密码生效，或已清除密码）。 */
    public static final int PASSWORD_CHANGE_SUCCESS = 1;
    /** 改密反馈：旧密码不匹配，修改被拒绝。 */
    public static final int PASSWORD_CHANGE_OLD_MISMATCH = 2;
    /** 改密反馈：新密码非法（长度超出 1..64），修改被忽略。 */
    public static final int PASSWORD_CHANGE_INVALID = 3;

    /** containerData 槽位索引：GUI 视图状态机。 */
    public static final int DATA_VIEW = 0;
    /** containerData 槽位索引：注册反馈状态。 */
    public static final int DATA_STATUS = 1;
    /** containerData 槽位索引：网格密码标记（1 = 已设）。 */
    public static final int DATA_PASSWORD_SET = 2;
    /** containerData 槽位索引：改密反馈打包值 {@code (seq << 2) | code}（seq 随尝试递增）。 */
    public static final int DATA_PASSWORD_STATUS = 3;

    /** displayName 最大长度（对齐网格密码上限，防止恶意撑爆存档）。 */
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;

    private static final int DATA_SLOT_COUNT = 4;

    /** 仅客户端构造：由 {@code MenuType} 调用。 */
    public AccessMenu(int containerId, Inventory playerInv) {
        super(AccessRegistration.ACCESS_MENU.get(), containerId);
        this.be = null;
        this.owner = null;
        this.statusData = new SimpleContainerData(DATA_SLOT_COUNT);
        this.addDataSlots(this.statusData);
    }

    /** 服务端构造：持有方块实体引用以执行业务逻辑，并记住打开菜单的玩家。 */
    public AccessMenu(int containerId, Inventory playerInv, AccessBlockEntity be, ServerPlayer owner) {
        super(AccessRegistration.ACCESS_MENU.get(), containerId);
        this.be = be;
        this.owner = owner;
        this.statusData = new SimpleContainerData(DATA_SLOT_COUNT);
        this.addDataSlots(this.statusData);
        refreshStatusFromBlockEntity();
        // 身份 payload 不在构造器发送：此时客户端菜单尚未打开（先处理开菜单包），
        // 包序会先到客户端而被丢弃；等首个 broadcastChanges（服务端下一 tick，
        // 此时客户端已收到开菜单包）再推送，lastSentIdentity 保持 null 触发首推。
    }

    // ==================== 字段 ====================

    private final ContainerData statusData;
    /** 仅服务端构造可用（客户端为 null）。 */
    private final AccessBlockEntity be;
    /** 仅服务端构造可用：打开菜单的玩家（推送身份 payload / 权限判定用）。 */
    private final ServerPlayer owner;
    /** 客户端收到的身份信息（{@link GridIdentityPayload} 写入，服务端为 null）。 */
    private GridIdentityPayload clientIdentity;
    /** 服务端最近一次推给客户端的身份信息，用于比对变化（避免每 tick 重复发包）。 */
    private GridIdentityPayload lastSentIdentity;

    // ==================== 实时状态刷新 ====================

    /**
     * 服务端每 tick 广播变化前，先从方块实体刷新状态与身份信息，实现
     * 「网格变化 / 账号注册 / 权限分层变化后客户端无需重开菜单即可看到最新视图」。
     */
    @Override
    public void broadcastChanges() {
        refreshStatusFromBlockEntity();
        syncIdentityToClient();
        super.broadcastChanges();
    }

    /**
     * 依据方块实体最新状态重算 {@code containerData}（视图状态机 + 反馈状态 +
     * 密码标记）。视图判定在服务端（{@link AccessBlockEntity#resolveView}），
     * 客户端只按视图渲染表单，真正的权限校验在各写操作的服务端入口。
     */
    private void refreshStatusFromBlockEntity() {
        if (this.be == null) {
            return;
        }
        int view = this.be.resolveView(this.owner);
        int status = this.be.getLastRegistrationStatus();
        if (view == VIEW_NO_CONTROLLER) {
            status = STATUS_NO_CONTROLLER;
        } else if (view == VIEW_REGISTER_ACCOUNT || view == VIEW_REGISTER_GRID) {
            // 未注册态不再保留过期的注册反馈（如网格被他人删除后的「注册成功」残影）
            status = STATUS_IDLE;
        }
        this.statusData.set(DATA_VIEW, view);
        this.statusData.set(DATA_STATUS, status);
        this.statusData.set(DATA_PASSWORD_SET, this.be.isPasswordSet() ? 1 : 0);
        this.statusData.set(DATA_PASSWORD_STATUS, this.be.getPasswordChangePacked());
    }

    /**
     * 若身份信息（accessId/displayName/owner 标记）相对上次已变化，
     * 把最新 {@link GridIdentityPayload} 推给打开菜单的玩家（仅服务端有效）。
     */
    private void syncIdentityToClient() {
        if (this.be == null || this.owner == null) {
            return;
        }
        GridIdentityPayload payload = buildIdentityPayload();
        if (payload.equals(this.lastSentIdentity)) {
            return;
        }
        this.lastSentIdentity = payload;
        PacketDistributor.sendToPlayer(this.owner, payload);
    }

    /**
     * @return 当前身份信息 payload；未注册时 accessId/displayName/ownerName 为空串，
     *         无控制器时 dimension 为空串、坐标为 {@code BlockPos.ZERO}
     */
    private GridIdentityPayload buildIdentityPayload() {
        var anchor = this.be.getControllerAnchor();
        var identity = anchor.flatMap(this.be::getIdentity);
        String dimension = "";
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.ZERO;
        if (anchor.isPresent()) {
            ControllerAnchor value = anchor.get();
            dimension = value.levelKey().location().toString();
            pos = value.pos();
        }
        return new GridIdentityPayload(
                this.containerId,
                identity.map(value -> value.accessId().toString()).orElse(""),
                identity.map(value -> value.displayName()).orElse(""),
                dimension,
                pos,
                this.be.hasAccount(this.owner.getUUID()),
                identity.map(value -> this.be.isOwnerOf(value, this.owner)).orElse(false),
                identity.map(value -> this.be.ownerNameOf(value)).orElse(""));
    }

    // ==================== 账号注册（C2S payload 入口） ====================

    /**
     * 处理 {@link AccountRegisterPayload} 提交的账号密码（仅服务端菜单；主线程）。
     *
     * <p>成功后账号镜像与视图状态立即刷新，客户端无需重开 GUI 即进入下一视图。</p>
     *
     * @param password 账号密码原文
     */
    public void handleAccountRegister(String password) {
        if (this.be == null || this.owner == null) {
            return;
        }
        this.be.registerAccount(this.owner, password);
        this.lastSentIdentity = null;
        refreshStatusFromBlockEntity();
        syncIdentityToClient();
    }

    // ==================== 注册网格（C2S payload 入口） ====================

    /**
     * 处理 {@link GridRegisterPayload} 提交的名称与密码（仅服务端菜单；主线程）。
     *
     * <p>服务端校验账号存在与网格未注册；成功后身份镜像缓存立即刷新（网页列表
     * 无需等待采样周期）并把最新身份与状态推回客户端。</p>
     *
     * @param displayName 显示名（空白由服务端回退默认名）
     * @param password    网格密码原文（空串 = 公开网格）
     */
    public void handleRegister(String displayName, String password) {
        if (this.be == null || this.owner == null) {
            return;
        }
        this.be.tryRegisterGrid(this.owner, displayName, password);
        this.lastSentIdentity = null;
        refreshStatusFromBlockEntity();
        syncIdentityToClient();
    }

    // ==================== 改名（C2S payload 入口） ====================

    /**
     * 处理 {@link GridRenamePayload} 提交的新显示名（仅服务端菜单；主线程）。
     *
     * <p>服务端校验「账号存在 + 玩家 == owner + 网格存在」，非本人网格拒绝；
     * 成功后身份镜像缓存立即刷新并把最新身份推回客户端。</p>
     *
     * @param displayName 新显示名
     */
    public void handleRename(String displayName) {
        if (this.be == null || this.owner == null) {
            return;
        }
        if (this.be.renameGrid(this.owner, displayName)) {
            this.lastSentIdentity = null;
            syncIdentityToClient();
        }
    }

    // ==================== 改密（C2S payload 入口） ====================

    /**
     * 处理 {@link GridChangePasswordPayload} 提交的旧/新密码（仅服务端菜单；主线程）。
     *
     * <p>服务端校验 owner 权限与旧密码（未设密码时旧密码须为空串）；新密码为
     * 空串 = 清除（恢复公开网格）。结果（成功 / 旧密码不匹配 / 新密码非法）
     * 由方块实体写入反馈状态，经 {@code containerData[3]} 打包值同步回客户端
     * 展示；处理后刷新密码标记同步回客户端。</p>
     *
     * @param oldPassword 旧密码原文
     * @param newPassword 新密码原文；空串 = 清除
     */
    public void handleChangePassword(String oldPassword, String newPassword) {
        if (this.be == null || this.owner == null) {
            return;
        }
        this.be.changeGridPassword(this.owner, oldPassword, newPassword);
        refreshStatusFromBlockEntity();
    }

    // ==================== 删除网格（C2S payload 入口） ====================

    /**
     * 处理 {@link GridDeletePayload}（仅服务端菜单；主线程）。
     *
     * <p>服务端校验 owner 权限后级联清理（身份 + 快照 + 命令记录）；删除后
     * 接入方块回未注册态，GUI 经状态刷新原地切到注册视图。</p>
     */
    public void handleDelete() {
        if (this.be == null || this.owner == null) {
            return;
        }
        if (this.be.deleteGrid(this.owner)) {
            this.lastSentIdentity = null;
            refreshStatusFromBlockEntity();
            syncIdentityToClient();
        }
    }

    // ==================== 快速访问状态 ====================

    /** 提供客户端屏幕读取的状态同步槽。 */
    public ContainerData getStatusData() {
        return this.statusData;
    }

    /** 客户端屏幕读取服务端推送的身份信息（未收到前为 null）。 */
    public GridIdentityPayload getClientIdentity() {
        return this.clientIdentity;
    }

    /** 由 {@link GridIdentityPayload} 客户端处理器写入。 */
    public void setClientIdentity(GridIdentityPayload clientIdentity) {
        this.clientIdentity = clientIdentity;
    }

    /** 该菜单没有物品槽，因此禁止 shift 点击移动物品。 */
    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    /** 这个 GUI 是短生命周期菜单，始终允许保留打开状态。 */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
