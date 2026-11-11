package com.ae2webmanagement.ae2webmanagementmod.access.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import com.ae2webmanagement.ae2webmanagementmod.access.AccessMenu;
import com.ae2webmanagement.ae2webmanagementmod.access.AccountRegisterPayload;
import com.ae2webmanagement.ae2webmanagementmod.access.GridChangePasswordPayload;
import com.ae2webmanagement.ae2webmanagementmod.access.GridDeletePayload;
import com.ae2webmanagement.ae2webmanagementmod.access.GridIdentityPayload;
import com.ae2webmanagement.ae2webmanagementmod.access.GridRegisterPayload;
import com.ae2webmanagement.ae2webmanagementmod.access.GridRenamePayload;
import com.ae2webmanagement.ae2webmanagementmod.data.GridPasswords;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 「接入」方块的 GUI 屏幕（仅客户端加载）。
 *
 * <p>roadmap §11 账号制权限分层：按服务端每 tick 推送的视图状态机
 * （{@code containerData[0]}）在以下视图间切换，视图变化时重建控件
 * （注册账号 / 注册网格 / 只读 / 管理），无需重开 GUI：</p>
 * <ul>
 *   <li>无控制器 —— 仅提示；</li>
 *   <li>无账号 —— 账号注册表单（密码 + 确认，确认仅客户端比对）；</li>
 *   <li>网格未注册 —— 名称 + 密码表单（都可空 = 默认名 + 公开网格）；</li>
 *   <li>他人网格 —— 只读视图（显示创建者名，服务端拒绝一切写操作）；</li>
 *   <li>本人网格 —— 改名 / 改密（旧 + 新）/ 删除（两段式确认）。</li>
 * </ul>
 *
 * <p>身份信息（accessId/displayName/控制器位置/ownerName）由
 * {@link GridIdentityPayload} 服务端推送；真正的权限校验在服务端，
 * 客户端表单只是入口。</p>
 */
public final class AccessScreen extends AbstractContainerScreen<AccessMenu> {

    private static final int PASSWORD_STATUS_NONE = 0;
    private static final int PASSWORD_STATUS_SET = 1;

    // ==================== 控件（按视图创建） ====================

    private EditBox accountPasswordBox;
    private EditBox accountConfirmBox;
    private EditBox gridNameBox;
    private EditBox gridPasswordBox;
    private EditBox renameBox;
    private EditBox oldPasswordBox;
    private EditBox newPasswordBox;
    private Button deleteButton;

    /** 当前已构建控件对应的视图（-1 = 尚未构建；视图变化时重建）。 */
    private int builtView = -1;
    /** 删除按钮的两段式确认状态：第一次点击仅置位，再次点击才发送。 */
    private boolean deleteArmed = false;
    /** 客户端本地校验提示（如两次密码不一致）；空串 = 不显示。 */
    private String localHint = "";

    public AccessScreen(AccessMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 240;
        this.imageHeight = 120;
    }

    /** 各视图的面板高度（init 时按视图设定，super.init() 再据此计算 topPos）。 */
    private static int heightOf(int view) {
        return switch (view) {
            case AccessMenu.VIEW_REGISTER_ACCOUNT -> 180;
            case AccessMenu.VIEW_REGISTER_GRID -> 160;
            case AccessMenu.VIEW_READONLY -> 108;
            case AccessMenu.VIEW_MANAGE -> 244;
            default -> 92; // VIEW_NO_CONTROLLER
        };
    }

    /** 初始化 GUI 尺寸对应的坐标，并按当前视图添加控件。 */
    @Override
    protected void init() {
        int view = this.menu.getStatusData().get(AccessMenu.DATA_VIEW);
        this.builtView = view;
        this.imageHeight = heightOf(view);
        super.init();
        switch (view) {
            case AccessMenu.VIEW_REGISTER_ACCOUNT -> initAccountRegisterWidgets();
            case AccessMenu.VIEW_REGISTER_GRID -> initGridRegisterWidgets();
            case AccessMenu.VIEW_MANAGE -> initManageWidgets();
            default -> {
                // VIEW_NO_CONTROLLER / VIEW_READONLY：无表单控件
            }
        }
    }

    /** 无账号视图：账号注册表单（密码 + 确认，确认仅客户端比对）。 */
    private void initAccountRegisterWidgets() {
        this.accountPasswordBox = addMaskedBox(94, "gui.ae2webmanagement.account.password");
        this.accountConfirmBox = addMaskedBox(122, "gui.ae2webmanagement.account.confirm");
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.ae2webmanagement.button.register_account"), b -> onRegisterAccount())
                .pos(this.leftPos + 70, this.topPos + 144)
                .size(100, 20)
                .build());
    }

    /** 未注册网格视图：名称 + 密码表单（都可空 = 默认名 + 公开网格）。 */
    private void initGridRegisterWidgets() {
        this.gridNameBox = addPlainBox(80, AccessMenu.MAX_DISPLAY_NAME_LENGTH, "gui.ae2webmanagement.rename.label");
        this.gridPasswordBox = addMaskedBox(108, "gui.ae2webmanagement.password.label");
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.ae2webmanagement.button.register"), b -> onRegisterGrid())
                .pos(this.leftPos + 70, this.topPos + 130)
                .size(100, 20)
                .build());
    }

    /** 本人网格视图：改名区 + 改密区（旧 + 新）+ 删除按钮（两段式确认）。 */
    private void initManageWidgets() {
        this.renameBox = addPlainBox(94, AccessMenu.MAX_DISPLAY_NAME_LENGTH, "gui.ae2webmanagement.rename.label");
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.ae2webmanagement.button.rename"), b -> onRename())
                .pos(this.leftPos + 10, this.topPos + 114)
                .size(105, 20)
                .build());
        this.oldPasswordBox = addMaskedBox(148, "gui.ae2webmanagement.password.old");
        this.newPasswordBox = addMaskedBox(176, "gui.ae2webmanagement.password.new");
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.ae2webmanagement.button.change_password"), b -> onChangePassword())
                .pos(this.leftPos + 10, this.topPos + 196)
                .size(105, 20)
                .build());
        this.deleteButton = Button.builder(
                Component.translatable("gui.ae2webmanagement.button.delete"), b -> onDelete())
                .pos(this.leftPos + 125, this.topPos + 196)
                .size(105, 20)
                .build();
        this.addRenderableWidget(this.deleteButton);
    }

    /** 添加普通文本输入框（显示原文）。 */
    private EditBox addPlainBox(int y, int maxLength, String labelKey) {
        EditBox box = new EditBox(this.font, this.leftPos + 10, this.topPos + y, this.imageWidth - 20, 16,
                Component.translatable(labelKey));
        box.setMaxLength(maxLength);
        this.addRenderableWidget(box);
        return box;
    }

    /** 添加掩码密码输入框（输入内容以圆点呈现，避免旁观者看到明文）。 */
    private EditBox addMaskedBox(int y, String labelKey) {
        EditBox box = new EditBox(this.font, this.leftPos + 10, this.topPos + y, this.imageWidth - 20, 16,
                Component.translatable(labelKey));
        box.setMaxLength(GridPasswords.MAX_PASSWORD_LENGTH);
        box.setFormatter((value, index) -> Component.literal("•".repeat(value.length())).getVisualOrderText());
        this.addRenderableWidget(box);
        return box;
    }

    /** 先绘制普通容器内容，再绘制身份信息、状态与视图专属文字。 */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 视图变化（账号注册成功 / 网格注册或删除 / 权限分层变化）→ 重建控件，无需重开 GUI
        int view = this.menu.getStatusData().get(AccessMenu.DATA_VIEW);
        if (view != this.builtView) {
            this.deleteArmed = false;
            this.localHint = "";
            rebuildWidgets();
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderDynamicText(guiGraphics, view);
    }

    /** 绘制无纹理 GUI 的半透明底色。 */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xCC101010);
    }

    /** 在面板顶部绘制居中标题；分区小标题与动态文字在 {@link #renderDynamicText}。 */
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleX = (this.imageWidth - this.font.width(this.title)) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, 8, 0xFFFFFF, false);
    }

    /** 绘制身份信息、状态消息、分区小标题与客户端提示（视图相关）。 */
    private void renderDynamicText(GuiGraphics guiGraphics, int view) {
        GridIdentityPayload identity = this.menu.getClientIdentity();
        boolean hasIdentity = identity != null && !identity.accessId().isEmpty();

        // 身份信息（服务端推送的字符串；未收到 payload 前显示占位）
        drawLine(guiGraphics, 32, "accessId: " + truncated(
                hasIdentity ? identity.accessId() : translate("gui.ae2webmanagement.identity.unregistered"), 40));
        drawLine(guiGraphics, 44, "displayName: " + truncated(
                hasIdentity ? identity.displayName() : translate("gui.ae2webmanagement.identity.unregistered"), 40));
        drawLine(guiGraphics, 56, "controller: " + truncated(
                identity != null && !identity.dimension().isEmpty()
                        ? identity.dimension() + " " + identity.pos().toShortString()
                        : translate("gui.ae2webmanagement.status.no_controller"), 44));
        if (hasIdentity) {
            String ownerName = identity.ownerName().isEmpty()
                    ? translate("gui.ae2webmanagement.identity.unowned")
                    : identity.ownerName();
            drawLine(guiGraphics, 68, "owner: " + truncated(ownerName, 40));
        }

        // 分区小标题 + 状态消息（视图相关）
        Component status = getStatusMessage(view);
        if (status != null) {
            int statusX = this.leftPos + (this.imageWidth - this.font.width(status)) / 2;
            guiGraphics.drawString(this.font, status, statusX, this.topPos + 20, 0xE0E0E0, false);
        }
        switch (view) {
            case AccessMenu.VIEW_REGISTER_ACCOUNT -> {
                drawLabel(guiGraphics, 84, "gui.ae2webmanagement.account.password");
                drawLabel(guiGraphics, 112, "gui.ae2webmanagement.account.confirm");
            }
            case AccessMenu.VIEW_REGISTER_GRID -> {
                drawLabel(guiGraphics, 70, "gui.ae2webmanagement.rename.label");
                drawLabel(guiGraphics, 98, "gui.ae2webmanagement.password.label");
            }
            case AccessMenu.VIEW_MANAGE -> {
                drawLabel(guiGraphics, 84, "gui.ae2webmanagement.rename.label");
                drawLabel(guiGraphics, 138, "gui.ae2webmanagement.password.old");
                drawLabel(guiGraphics, 166, "gui.ae2webmanagement.password.new");
                drawCentered(guiGraphics, 220, getPasswordStatusMessage());
            }
            default -> {
                // 无控制器 / 只读视图：无表单小标题
            }
        }

        // 客户端本地校验提示（红色）
        if (!this.localHint.isEmpty()) {
            Component hint = Component.literal(this.localHint);
            int hintX = this.leftPos + (this.imageWidth - this.font.width(hint)) / 2;
            int hintY = this.topPos + this.imageHeight - 8;
            guiGraphics.drawString(this.font, hint, hintX, hintY, 0xFF8080, false);
        }
    }

    /** 在指定相对坐标绘制一行浅灰文字。 */
    private void drawLine(GuiGraphics guiGraphics, int y, String text) {
        guiGraphics.drawString(this.font, text, this.leftPos + 10, this.topPos + y, 0xE0E0E0, false);
    }

    /** 在指定相对坐标绘制分区小标题（灰色）。 */
    private void drawLabel(GuiGraphics guiGraphics, int y, String key) {
        guiGraphics.drawString(this.font, Component.translatable(key), this.leftPos + 10, this.topPos + y, 0xA0A0A0, false);
    }

    /** 在指定相对坐标绘制居中文字。 */
    private void drawCentered(GuiGraphics guiGraphics, int y, Component text) {
        int x = this.leftPos + (this.imageWidth - this.font.width(text)) / 2;
        guiGraphics.drawString(this.font, text, x, this.topPos + y, 0xE0E0E0, false);
    }

    // ==================== 按钮 → 客户端校验 + 发包 ====================

    /** 提交账号注册：客户端比对两次密码一致且非空后发送（服务端只收一个密码）。 */
    private void onRegisterAccount() {
        String password = valueOf(this.accountPasswordBox);
        String confirm = valueOf(this.accountConfirmBox);
        if (password.isEmpty() || confirm.isEmpty()) {
            this.localHint = translate("gui.ae2webmanagement.account.empty");
            return;
        }
        if (!password.equals(confirm)) {
            this.localHint = translate("gui.ae2webmanagement.account.mismatch");
            return;
        }
        this.localHint = "";
        PacketDistributor.sendToServer(new AccountRegisterPayload(this.menu.containerId, password));
        this.accountPasswordBox.setValue("");
        this.accountConfirmBox.setValue("");
    }

    /** 提交网格注册：名称与密码都可空（默认名 + 公开网格），校验交服务端。 */
    private void onRegisterGrid() {
        String name = valueOf(this.gridNameBox);
        String password = valueOf(this.gridPasswordBox);
        this.localHint = "";
        PacketDistributor.sendToServer(new GridRegisterPayload(this.menu.containerId, name.trim(), password));
        this.gridNameBox.setValue("");
        this.gridPasswordBox.setValue("");
    }

    /** 提交改名：非空白时发送（空白输入直接忽略）。 */
    private void onRename() {
        String name = valueOf(this.renameBox);
        if (!name.isBlank()) {
            PacketDistributor.sendToServer(new GridRenamePayload(this.menu.containerId, name.trim()));
            this.renameBox.setValue("");
        }
    }

    /** 提交改密：旧密码 + 新密码（新密码空串 = 清除），校验交服务端。 */
    private void onChangePassword() {
        String oldPassword = valueOf(this.oldPasswordBox);
        String newPassword = valueOf(this.newPasswordBox);
        this.localHint = "";
        PacketDistributor.sendToServer(
                new GridChangePasswordPayload(this.menu.containerId, oldPassword, newPassword));
        this.oldPasswordBox.setValue("");
        this.newPasswordBox.setValue("");
    }

    /** 删除网格：两段式确认（第一次点击置位并改按钮文案，再次点击才发送）。 */
    private void onDelete() {
        if (!this.deleteArmed) {
            this.deleteArmed = true;
            this.deleteButton.setMessage(Component.translatable("gui.ae2webmanagement.button.delete_confirm"));
            this.localHint = translate("gui.ae2webmanagement.delete.hint");
            return;
        }
        this.deleteArmed = false;
        this.localHint = "";
        PacketDistributor.sendToServer(new GridDeletePayload(this.menu.containerId));
    }

    // ==================== 状态文字 ====================

    /** 把服务端同步的状态码按视图转换成本地化提示文本（{@code null} = 不显示）。 */
    private Component getStatusMessage(int view) {
        int status = this.menu.getStatusData().get(AccessMenu.DATA_STATUS);
        return switch (view) {
            case AccessMenu.VIEW_NO_CONTROLLER -> Component.translatable("gui.ae2webmanagement.status.no_controller");
            case AccessMenu.VIEW_REGISTER_ACCOUNT -> Component.translatable("gui.ae2webmanagement.account.hint");
            case AccessMenu.VIEW_REGISTER_GRID -> switch (status) {
                case AccessMenu.STATUS_CREATED -> Component.translatable("gui.ae2webmanagement.status.created");
                case AccessMenu.STATUS_ALREADY_EXISTS -> Component.translatable("gui.ae2webmanagement.status.already_exists");
                default -> Component.translatable("gui.ae2webmanagement.status.idle");
            };
            case AccessMenu.VIEW_READONLY -> {
                GridIdentityPayload identity = this.menu.getClientIdentity();
                String ownerName = identity == null ? "" : identity.ownerName();
                yield ownerName.isEmpty()
                        ? Component.translatable("gui.ae2webmanagement.status.readonly_unowned")
                        : Component.translatable("gui.ae2webmanagement.status.readonly", ownerName);
            }
            case AccessMenu.VIEW_MANAGE -> switch (status) {
                case AccessMenu.STATUS_CREATED -> Component.translatable("gui.ae2webmanagement.status.created");
                case AccessMenu.STATUS_ALREADY_EXISTS -> Component.translatable("gui.ae2webmanagement.status.already_exists");
                default -> null;
            };
            default -> null;
        };
    }

    /** 把服务端同步的密码标记转换成本地化提示文本（管理视图底部）。 */
    private Component getPasswordStatusMessage() {
        int flag = this.menu.getStatusData().get(AccessMenu.DATA_PASSWORD_SET);
        return flag == PASSWORD_STATUS_SET
                ? Component.translatable("gui.ae2webmanagement.status.password_set")
                : Component.translatable("gui.ae2webmanagement.status.password_none");
    }

    // ==================== 工具 ====================

    /** 读取输入框内容（控件未创建时为空串）。 */
    private static String valueOf(EditBox box) {
        return box == null ? "" : box.getValue();
    }

    /** 按宽度截断文本，避免超长字符串（如 UUID）溢出面板。 */
    private String truncated(String value, int maxWidth) {
        return this.font.plainSubstrByWidth(value, maxWidth);
    }

    /** 取本地化文本的原始字符串（用于拼进普通文本行）。 */
    private String translate(String key) {
        return Component.translatable(key).getString();
    }
}
