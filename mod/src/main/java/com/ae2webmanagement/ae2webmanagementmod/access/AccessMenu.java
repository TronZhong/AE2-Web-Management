package com.ae2webmanagement.ae2webmanagementmod.access;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * 「接入」方块的容器菜单，持有服务端 ↔ 客户端共享的状态数据。
 *
 * <p>状态值由 {@code containerData} 同步：
 * <ul>
 *   <li>{@code containerData[0]} —— 注册流程状态：
 *       {@link #STATUS_NO_CONTROLLER} / {@link #STATUS_IDLE} /
 *       {@link #STATUS_CREATED} / {@link #STATUS_ALREADY_EXISTS}</li>
 *   <li>{@code containerData[1]} —— 网格密码标记：1 = 已设密码，0 = 未设（公开）</li>
 * </ul>
 *
 * <p>按钮 ID 0 —— 「注册网络」。密码输入走 {@link GridPasswordPayload}（字符串无法进 containerData）。
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

    private static final int BUTTON_REGISTER = 0;

    /** 仅客户端构造：由 {@code MenuType} 调用。 */
    public AccessMenu(int containerId, Inventory playerInv) {
        super(AccessRegistration.ACCESS_MENU.get(), containerId);
        this.be = null;
        this.statusData = new SimpleContainerData(2);
        this.addDataSlots(this.statusData);
    }

    /** 服务端构造：持有方块实体引用以执行业务逻辑。 */
    public AccessMenu(int containerId, Inventory playerInv, AccessBlockEntity be) {
        super(AccessRegistration.ACCESS_MENU.get(), containerId);
        this.be = be;
        this.statusData = new SimpleContainerData(2);
        this.addDataSlots(this.statusData);

        if (be.getControllerAnchor().isEmpty()) {
            this.statusData.set(DATA_STATUS, STATUS_NO_CONTROLLER);
        } else {
            this.statusData.set(DATA_STATUS, STATUS_IDLE);
        }
        this.statusData.set(DATA_PASSWORD_SET, be.isPasswordSet() ? 1 : 0);
    }

    // ==================== 字段 ====================

    private final ContainerData statusData;
    /** 仅服务端构造可用（客户端为 null）。 */
    private final AccessBlockEntity be;

    // ==================== 按钮 → 业务逻辑 ====================

    /** 处理 GUI 中的按钮点击；只有服务端菜单能调用方块实体的注册逻辑。 */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_REGISTER && be != null) {
            be.tryRegisterNetwork();
            // 更新状态同步回客户端
            this.statusData.set(DATA_STATUS, be.getLastRegistrationStatus());
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

    // ==================== 快速访问状态 ====================

    /** 提供客户端屏幕读取的状态同步槽。 */
    public ContainerData getStatusData() {
        return this.statusData;
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
