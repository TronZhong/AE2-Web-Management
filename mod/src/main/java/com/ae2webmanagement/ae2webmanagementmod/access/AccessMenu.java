package com.ae2webmanagement.ae2webmanagementmod.access;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

/**
 * 「接入」方块的容器菜单，持有服务端 ↔ 客户端共享的状态数据。
 *
 * <p>状态值由 {@code containerData[0]} 同步：
 * <ul>
 *   <li>{@link #STATUS_NO_CONTROLLER} —— 尚未定位到 ME 控制器</li>
 *   <li>{@link #STATUS_IDLE} —— 已定位控制器，等待操作</li>
 *   <li>{@link #STATUS_CREATED} —— 网络身份刚创建成功</li>
 *   <li>{@link #STATUS_ALREADY_EXISTS} —— 该控制器锚点已有身份</li>
 * </ul>
 *
 * <p>按钮 ID 0 —— 「注册网络」
 */
public class AccessMenu extends AbstractContainerMenu {

    public static final int STATUS_NO_CONTROLLER = 0;
    public static final int STATUS_IDLE = 1;
    public static final int STATUS_CREATED = 2;
    public static final int STATUS_ALREADY_EXISTS = 3;

    private static final int BUTTON_REGISTER = 0;

    /** 仅客户端构造：由 {@code MenuType} 调用。 */
    public AccessMenu(int containerId, Inventory playerInv) {
        super(AccessRegistration.ACCESS_MENU.get(), containerId);
        this.be = null;
        this.statusData = new SimpleContainerData(1);
        this.addDataSlots(this.statusData);
        this.addPlayerSlots(playerInv);
    }

    /** 服务端构造：持有方块实体引用以执行业务逻辑。 */
    public AccessMenu(int containerId, Inventory playerInv, AccessBlockEntity be) {
        super(AccessRegistration.ACCESS_MENU.get(), containerId);
        this.be = be;
        this.statusData = new SimpleContainerData(1);
        this.addDataSlots(this.statusData);
        this.addPlayerSlots(playerInv);

        if (be.getControllerAnchor().isEmpty()) {
            this.statusData.set(0, STATUS_NO_CONTROLLER);
        } else {
            this.statusData.set(0, STATUS_IDLE);
        }
    }

    // ==================== 字段 ====================

    private final ContainerData statusData;
    /** 仅服务端构造可用（客户端为 null）。 */
    private final AccessBlockEntity be;

    // ==================== 按钮 → 业务逻辑 ====================

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_REGISTER && be != null) {
            be.tryRegisterNetwork();
            // 更新状态同步回客户端
            this.statusData.set(0, be.getLastRegistrationStatus());
            return true;
        }
        return false;
    }

    // ==================== 快速访问状态 ====================

    public ContainerData getStatusData() {
        return this.statusData;
    }

    // ==================== 玩家物品栏 ====================

    private void addPlayerSlots(Inventory playerInv) {
        // 快捷栏 (0–8) → 物品栏 (9–35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, 9 + row * 9 + col,
                        8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col,
                    8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY; // 无物品移入移出逻辑
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}