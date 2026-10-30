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
 * <p>布局：标题 + 状态文字 + Register 按钮。
 */
public final class AccessScreen extends AbstractContainerScreen<AccessMenu> {

    public AccessScreen(AccessMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 200;
        this.imageHeight = 96;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.ae2webmanagement.button.register"), this::onRegisterPress)
                        .pos(this.leftPos + 62, this.topPos + 58)
                        .size(76, 20)
                        .build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xCC101010);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleX = (this.imageWidth - this.font.width(this.title)) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, 8, 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        Component statusMsg = getStatusMessage();
        int statusX = this.leftPos + (this.imageWidth - this.font.width(statusMsg)) / 2;
        guiGraphics.drawString(this.font, statusMsg, statusX, this.topPos + 30, 0xE0E0E0, false);
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
