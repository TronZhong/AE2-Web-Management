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
 *   <li>{@code containerData[0]} —— 注册流程状态：
 *       {@link #STATUS_NO_CONTROLLER} / {@link #STATUS_IDLE} /
 *       {@link #STATUS_CREATED} / {@link #STATUS_ALREADY_EXISTS}</li>
 *   <li>{@code containerData[1]} —— 网格密码标记：1 = 已设密码，0 = 未设（公开）</li>
 * </ul>
 *
 * <p>按钮 ID 0 —— 「注册网格」。密码输入走 {@link GridPasswordPayload}；
 * displayName 改名走 {@link GridRenamePayload}；身份信息（accessId/displayName/
 * 控制器位置）经 {@link GridIdentityPayload} 推给客户端展示（字符串无法进
 * containerData，roadmap 已完成项：GUI 迭代）。</p>
 */
public class AccessMenu extends AbstractContainerMenu {

    public static final int STATUS_NO_CONTROLLER = 0;
    public static final int STATUS_IDLE = 1;
    public static final int STATUS_CREATED = 2;
    public static final int STATUS_ALREADY_EXISTS = 3;

    /** containerData 槽位索引：注册流程状态。 */
    public static final int DATA_STATUS = 0;
    /** containerData 槽位索引：网格密码标记（1 = 已设）。 */
    public static final int DATA_PASSWORD_SET = 1;

    /** displayName 最大长度（对齐网格密码上限，防止恶意撑爆存档）。 */
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;

    private static final int BUTTON_REGISTER = 0;

    /** 仅客户端构造：由 {@code MenuType} 调用。 */
    public AccessMenu(int containerId, Inventory playerInv) {
        super(AccessRegistration.ACCESS_MENU.get(), containerId);
        this.be = null;
        this.owner = null;
        this.statusData = new SimpleContainerData(2);
        this.addDataSlots(this.statusData);
    }

    /** 服务端构造：持有方块实体引用以执行业务逻辑，并记住打开菜单的玩家。 */
    public AccessMenu(int containerId, Inventory playerInv, AccessBlockEntity be, ServerPlayer owner) {
        super(AccessRegistration.ACCESS_MENU.get(), containerId);
        this.be = be;
        this.owner = owner;
        this.statusData = new SimpleContainerData(2);
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
    /** 仅服务端构造可用：打开菜单的玩家（推送身份 payload 用）。 */
    private final ServerPlayer owner;
    /** 客户端收到的身份信息（{@link GridIdentityPayload} 写入，服务端为 null）。 */
    private GridIdentityPayload clientIdentity;
    /** 服务端最近一次推给客户端的身份信息，用于比对变化（避免每 tick 重复发包）。 */
    private GridIdentityPayload lastSentIdentity;

    // ==================== 实时状态刷新 ====================

    /**
     * 服务端每 tick 广播变化前，先从方块实体刷新状态与身份信息，实现
     * 「网格变化（{@code onGridChanged}）后客户端无需重开菜单即可看到最新状态」
     * （roadmap 已完成项：GUI 状态实时刷新）。
     */
    @Override
    public void broadcastChanges() {
        refreshStatusFromBlockEntity();
        syncIdentityToClient();
        super.broadcastChanges();
    }

    /**
     * 依据方块实体最新状态重算 {@code containerData}（注册状态 + 密码标记）。
     * 服务端每 tick 调用；网格断开时自动回到 {@link #STATUS_NO_CONTROLLER}，
     * 网格在线时保留最近一次注册结果（CREATED / ALREADY_EXISTS / IDLE）。
     */
    private void refreshStatusFromBlockEntity() {
        if (this.be == null) {
            return;
        }
        if (this.be.getControllerAnchor().isEmpty()) {
            this.statusData.set(DATA_STATUS, STATUS_NO_CONTROLLER);
        } else {
            int status = this.be.getLastRegistrationStatus();
            if (status == STATUS_NO_CONTROLLER) {
                status = STATUS_IDLE; // 有控制器但尚未注册 / 网格刚变化
            }
            this.statusData.set(DATA_STATUS, status);
        }
        this.statusData.set(DATA_PASSWORD_SET, this.be.isPasswordSet() ? 1 : 0);
    }

    /**
     * 若身份信息（accessId/displayName/控制器位置）相对上次已变化，
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
     * @return 当前身份信息 payload；未注册时 accessId/displayName 为空串，
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
                pos);
    }

    // ==================== 按钮 → 业务逻辑 ====================

    /** 处理 GUI 中的按钮点击；只有服务端菜单能调用方块实体的注册逻辑。 */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_REGISTER && be != null) {
            be.tryRegisterGrid();
            // 注册后立刻刷新状态并把最新身份推给客户端（无需等下一 tick）
            this.lastSentIdentity = null;
            refreshStatusFromBlockEntity();
            syncIdentityToClient();
            return true;
        }
        return false;
    }

    // ==================== 网格密码（C2S payload 入口） ====================

    /**
     * 处理 {@link GridPasswordPayload} 提交的密码（仅服务端菜单；主线程）。
     *
     * <p>空串 = 清除；非空交给方块实体校验并写哈希。处理后刷新密码标记同步回客户端。</p>
     *
     * @param password 密码原文；空串表示清除
     */
    public void handleSetPassword(String password) {
        if (this.be == null) {
            return;
        }
        this.be.setGridPassword(password);
        this.statusData.set(DATA_PASSWORD_SET, this.be.isPasswordSet() ? 1 : 0);
    }

    // ==================== 改名（C2S payload 入口） ====================

    /**
     * 处理 {@link GridRenamePayload} 提交的新显示名（仅服务端菜单；主线程）。
     *
     * <p>空白 / 超长由方块实体忽略；成功后身份镜像缓存立即刷新（网页列表
     * 无需等待下一采样周期），并把最新身份推回客户端。</p>
     *
     * @param displayName 新显示名
     */
    public void handleRename(String displayName) {
        if (this.be == null) {
            return;
        }
        if (this.be.renameGrid(displayName)) {
            this.lastSentIdentity = null;
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
