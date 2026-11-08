package com.ae2webmanagement.ae2webmanagementmod.access.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.world.entity.player.Inventory;

import com.ae2webmanagement.ae2webmanagementmod.access.AccessMenu;
import com.ae2webmanagement.ae2webmanagementmod.access.GridIdentityPayload;
import com.ae2webmanagement.ae2webmanagementmod.access.GridPasswordPayload;
import com.ae2webmanagement.ae2webmanagementmod.access.GridRenamePayload;
import com.ae2webmanagement.ae2webmanagementmod.data.GridPasswords;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 「接入」方块的 GUI 屏幕（仅客户端加载）。
 *
 * <p>布局：标题 + 注册状态 + 网格身份信息（accessId/displayName/控制器位置，
 * 由 {@link GridIdentityPayload} 服务端推送）+ 改名区（EditBox + 改名按钮）+
 * Register 按钮 + 网格密码区（EditBox 掩码显示、设置/清除按钮、密码状态）。
 * 注册状态与密码标记来自 menu containerData 服务端每 tick 实时同步；
 * 身份信息为字符串，走 S2C payload 通道。</p>
 */
public final class AccessScreen extends AbstractContainerScreen<AccessMenu> {

    private static final int PASSWORD_STATUS_NONE = 0;
    private static final int PASSWORD_STATUS_SET = 1;

    private EditBox passwordBox;
    private EditBox nameBox;

    public AccessScreen(AccessMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 220;
        this.imageHeight = 232;
    }

    /** 初始化 GUI 尺寸对应的坐标，并添加 Register / 改名 / 密码区控件。 */
    @Override
    protected void init() {
        super.init();

        // 改名区：EditBox + 改名按钮（提交新 displayName）
        this.nameBox = new EditBox(
                this.font,
                this.leftPos + 10,
                this.topPos + 116,
                this.imageWidth - 20,
                16,
                Component.translatable("gui.ae2webmanagement.rename.label"));
        this.nameBox.setMaxLength(AccessMenu.MAX_DISPLAY_NAME_LENGTH);
        this.addRenderableWidget(this.nameBox);
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.ae2webmanagement.button.rename"), this::onRenamePress)
                        .pos(this.leftPos + 70, this.topPos + 136)
                        .size(80, 20)
                        .build());

        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.ae2webmanagement.button.register"), this::onRegisterPress)
                        .pos(this.leftPos + 70, this.topPos + 78)
                        .size(80, 20)
                        .build());

        this.passwordBox = new EditBox(
                this.font,
                this.leftPos + 10,
                this.topPos + 174,
                this.imageWidth - 20,
                16,
                Component.translatable("gui.ae2webmanagement.password.label"));
        this.passwordBox.setMaxLength(GridPasswords.MAX_PASSWORD_LENGTH);
        // 掩码显示：输入内容以圆点呈现，避免旁观者看到明文
        this.passwordBox.setFormatter((value, index) -> Component.literal("•".repeat(value.length())).getVisualOrderText());
        this.addRenderableWidget(this.passwordBox);

        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.ae2webmanagement.button.password_set"), this::onSetPasswordPress)
                        .pos(this.leftPos + 10, this.topPos + 194)
                        .size(96, 20)
                        .build());
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.ae2webmanagement.button.password_clear"), this::onClearPasswordPress)
                        .pos(this.leftPos + 114, this.topPos + 194)
                        .size(96, 20)
                        .build());
    }

    /** 绘制无纹理 GUI 的半透明底色。 */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xCC101010);
    }

    /** 在面板顶部绘制居中标题与分区小标题；不渲染玩家物品栏标签。 */
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleX = (this.imageWidth - this.font.width(this.title)) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, 8, 0xFFFFFF, false);

        Component renameLabel = Component.translatable("gui.ae2webmanagement.rename.label");
        guiGraphics.drawString(this.font, renameLabel, 10, 102, 0xA0A0A0, false);

        Component passwordLabel = Component.translatable("gui.ae2webmanagement.password.label");
        guiGraphics.drawString(this.font, passwordLabel, 10, 160, 0xA0A0A0, false);
    }

    /** 先绘制普通容器内容，再绘制身份信息、注册状态与密码状态文字。 */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 网格身份信息（服务端推送的字符串；未收到 payload 前显示占位）
        GridIdentityPayload identity = this.menu.getClientIdentity();
        Component accessIdLine = Component.literal("accessId: " + truncated(
                identity != null && !identity.accessId().isEmpty()
                        ? identity.accessId()
                        : translate("gui.ae2webmanagement.identity.unregistered"), 36));
        guiGraphics.drawString(this.font, accessIdLine, this.leftPos + 10, this.topPos + 38, 0xE0E0E0, false);

        Component nameLine = Component.literal("displayName: " + truncated(
                identity != null && !identity.displayName().isEmpty()
                        ? identity.displayName()
                        : translate("gui.ae2webmanagement.identity.unregistered"), 36));
        guiGraphics.drawString(this.font, nameLine, this.leftPos + 10, this.topPos + 50, 0xE0E0E0, false);

        Component controllerLine = Component.literal("controller: " + truncated(
                identity != null && !identity.dimension().isEmpty()
                        ? identity.dimension() + " " + identity.pos().getX() + "," + identity.pos().getY() + "," + identity.pos().getZ()
                        : translate("gui.ae2webmanagement.status.no_controller"), 40));
        guiGraphics.drawString(this.font, controllerLine, this.leftPos + 10, this.topPos + 62, 0xE0E0E0, false);

        Component statusMsg = getStatusMessage();
        int statusX = this.leftPos + (this.imageWidth - this.font.width(statusMsg)) / 2;
        guiGraphics.drawString(this.font, statusMsg, statusX, this.topPos + 24, 0xE0E0E0, false);

        Component passwordStatus = getPasswordStatusMessage();
        int passwordX = this.leftPos + (this.imageWidth - this.font.width(passwordStatus)) / 2;
        guiGraphics.drawString(this.font, passwordStatus, passwordX, this.topPos + 220, 0xE0E0E0, false);
    }

    // ==================== 按钮 → 发包 ====================

    /** 点击 Register 后向服务端发送容器按钮包；实际注册逻辑只在服务端执行。 */
    private void onRegisterPress(Button button) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection()
                    .send(new ServerboundContainerButtonClickPacket(this.menu.containerId, 0));
        }
    }

    /** 点击「改名」：非空白时把 EditBox 内容作为新显示名提交（空白输入直接忽略）。 */
    private void onRenamePress(Button button) {
        String name = this.nameBox.getValue();
        if (!name.isBlank()) {
            PacketDistributor.sendToServer(new GridRenamePayload(this.menu.containerId, name.trim()));
            this.nameBox.setValue("");
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

    /** 点击「清除密码」：发送空串，服务端据此恢复公开网格。 */
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

    // ==================== 工具 ====================

    /** 按宽度截断文本，避免超长字符串（如 UUID）溢出面板。 */
    private String truncated(String value, int maxWidth) {
        return this.font.plainSubstrByWidth(value, maxWidth);
    }

    /** 取本地化文本的原始字符串（用于拼进普通文本行）。 */
    private String translate(String key) {
        return Component.translatable(key).getString();
    }
}
