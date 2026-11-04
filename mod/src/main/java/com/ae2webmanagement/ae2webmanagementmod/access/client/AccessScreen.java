package com.ae2webmanagement.ae2webmanagementmod.access.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.world.entity.player.Inventory;

import com.ae2webmanagement.ae2webmanagementmod.access.AccessMenu;
import com.ae2webmanagement.ae2webmanagementmod.access.GridPasswordPayload;
import com.ae2webmanagement.ae2webmanagementmod.data.GridPasswords;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 「接入」方块的 GUI 屏幕（仅客户端加载）。
 *
 * <p>布局：标题 + 注册状态 + Register 按钮 + 网格密码区（EditBox 掩码显示、
 * 设置/清除按钮、密码状态）。密码提交走 C2S {@link GridPasswordPayload}；
 * 「是否已设密码」由 menu containerData[1] 服务端同步。</p>
 */
public final class AccessScreen extends AbstractContainerScreen<AccessMenu> {

    private static final int PASSWORD_STATUS_NONE = 0;
    private static final int PASSWORD_STATUS_SET = 1;

    private EditBox passwordBox;

    public AccessScreen(AccessMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 220;
        this.imageHeight = 158;
    }

    /** 初始化 GUI 尺寸对应的坐标，并添加 Register 与密码区控件。 */
    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.ae2webmanagement.button.register"), this::onRegisterPress)
                        .pos(this.leftPos + 70, this.topPos + 38)
                        .size(80, 20)
                        .build());

        this.passwordBox = new EditBox(
                this.font,
                this.leftPos + 10,
                this.topPos + 80,
                this.imageWidth - 20,
                16,
                Component.translatable("gui.ae2webmanagement.password.label"));
        this.passwordBox.setMaxLength(GridPasswords.MAX_PASSWORD_LENGTH);
        // 掩码显示：输入内容以圆点呈现，避免旁观者看到明文
        this.passwordBox.setFormatter((value, index) -> Component.literal("•".repeat(value.length())).getVisualOrderText());
        this.addRenderableWidget(this.passwordBox);

        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.ae2webmanagement.button.password_set"), this::onSetPasswordPress)
                        .pos(this.leftPos + 10, this.topPos + 102)
                        .size(96, 20)
                        .build());
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.ae2webmanagement.button.password_clear"), this::onClearPasswordPress)
                        .pos(this.leftPos + 114, this.topPos + 102)
                        .size(96, 20)
                        .build());
    }

    /** 绘制无纹理 GUI 的半透明底色。 */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xCC101010);
    }

    /** 在面板顶部绘制居中标题与密码区小标题；不渲染玩家物品栏标签。 */
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleX = (this.imageWidth - this.font.width(this.title)) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, 8, 0xFFFFFF, false);
        Component passwordLabel = Component.translatable("gui.ae2webmanagement.password.label");
        guiGraphics.drawString(this.font, passwordLabel, 10, 70, 0xA0A0A0, false);
    }

    /** 先绘制普通容器内容，再绘制注册状态与密码状态两行文字。 */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        Component statusMsg = getStatusMessage();
        int statusX = this.leftPos + (this.imageWidth - this.font.width(statusMsg)) / 2;
        guiGraphics.drawString(this.font, statusMsg, statusX, this.topPos + 26, 0xE0E0E0, false);

        Component passwordStatus = getPasswordStatusMessage();
        int passwordX = this.leftPos + (this.imageWidth - this.font.width(passwordStatus)) / 2;
        guiGraphics.drawString(this.font, passwordStatus, passwordX, this.topPos + 128, 0xE0E0E0, false);
    }

    // ==================== 按钮 → 发包 ====================

    /** 点击 Register 后向服务端发送容器按钮包；实际注册逻辑只在服务端执行。 */
    private void onRegisterPress(Button button) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection()
                    .send(new ServerboundContainerButtonClickPacket(this.menu.containerId, 0));
        }
    }

    /** 点击「设置密码」：非空时把 EditBox 内容作为密码提交（空输入直接忽略）。 */
    private void onSetPasswordPress(Button button) {
        String password = this.passwordBox.getValue();
        if (!password.isEmpty()) {
            sendPassword(password);
            this.passwordBox.setValue("");
        }
    }

    /** 点击「清除密码」：发送空串，服务端据此恢复公开网络。 */
    private void onClearPasswordPress(Button button) {
        sendPassword("");
        this.passwordBox.setValue("");
    }

    /** 把密码（或空串=清除）通过 C2S payload 发给服务端。 */
    private void sendPassword(String password) {
        PacketDistributor.sendToServer(new GridPasswordPayload(this.menu.containerId, password));
    }

    // ==================== 状态文字 ====================

    /** 把服务端同步的状态码转换成本地化提示文本。 */
    private Component getStatusMessage() {
        int status = this.menu.getStatusData().get(AccessMenu.DATA_STATUS);
        return switch (status) {
            case AccessMenu.STATUS_NO_CONTROLLER -> Component.translatable("gui.ae2webmanagement.status.no_controller");
            case AccessMenu.STATUS_IDLE -> Component.translatable("gui.ae2webmanagement.status.idle");
            case AccessMenu.STATUS_CREATED -> Component.translatable("gui.ae2webmanagement.status.created");
            case AccessMenu.STATUS_ALREADY_EXISTS -> Component.translatable("gui.ae2webmanagement.status.already_exists");
            default -> Component.literal("?");
        };
    }

    /** 把服务端同步的密码标记转换成本地化提示文本。 */
    private Component getPasswordStatusMessage() {
        int flag = this.menu.getStatusData().get(AccessMenu.DATA_PASSWORD_SET);
        return flag == PASSWORD_STATUS_SET
                ? Component.translatable("gui.ae2webmanagement.status.password_set")
                : Component.translatable("gui.ae2webmanagement.status.password_none");
    }
}
