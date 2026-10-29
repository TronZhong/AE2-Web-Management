package com.ae2webmanagement.ae2webmanagementmod.access.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.world.entity.player.Inventory;

import com.ae2webmanagement.ae2webmanagementmod.access.AccessMenu;

/**
 * 「接入」方块的 GUI 屏幕（仅客户端加载）。
 *
 * <p>布局：标题 + 状态文字 + Register 按钮 + 玩家物品栏。
 */
public final class AccessScreen extends AbstractContainerScreen<AccessMenu> {

    public AccessScreen(AccessMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.ae2webmanagement.button.register"), this::onRegisterPress)
                        .pos(this.leftPos + 50, this.topPos + 38)
                        .size(76, 20)
                        .build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 无背景纹理——使用默认深灰底色
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        Component statusMsg = getStatusMessage();
        guiGraphics.drawString(this.font, statusMsg, this.leftPos + 8, this.topPos + 22, 0x404040, false);
    }

    // ==================== 按钮 → 发包 ====================

    private void onRegisterPress(Button button) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection()
                    .send(new ServerboundContainerButtonClickPacket(this.menu.containerId, 0));
        }
    }

    // ==================== 状态文字 ====================

    private Component getStatusMessage() {
        int status = this.menu.getStatusData().get(0);
        return switch (status) {
            case AccessMenu.STATUS_NO_CONTROLLER -> Component.translatable("gui.ae2webmanagement.status.no_controller");
            case AccessMenu.STATUS_IDLE -> Component.translatable("gui.ae2webmanagement.status.idle");
            case AccessMenu.STATUS_CREATED -> Component.translatable("gui.ae2webmanagement.status.created");
            case AccessMenu.STATUS_ALREADY_EXISTS -> Component.translatable("gui.ae2webmanagement.status.already_exists");
            default -> Component.literal("?");
        };
    }
}