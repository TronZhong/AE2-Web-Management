package com.ae2webmanagement.ae2webmanagementmod.access.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import org.lwjgl.glfw.GLFW;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;

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
 * 「接入」方块的 GUI 屏幕（仅客户端加载），整体为 AE2 风格：
 * 面板复用 {@code ae2:textures/guis/background.png}（九宫格绘制），输入框使用
 * AE2 的 {@link AETextField}，按钮使用 AE2 的 {@link AE2Button}，文字配色取自
 * AE2 官方调色板（{@code ae2:screens/common/palette.json}）。
 *
 * <p>roadmap §11 账号制权限分层：按服务端每 tick 推送的视图状态机
 * （{@code containerData[0]}）在以下视图间切换，视图变化时重建控件
 * （注册账号 / 注册网格 / 只读 / 管理），无需重开 GUI：</p>
 * <ul>
 *   <li>无控制器 —— 仅提示；</li>
 *   <li>无账号 —— 账号注册表单（密码 + 确认，确认仅客户端比对）；</li>
 *   <li>网格未注册 —— 名称 + 密码表单（都可空 = 默认名 + 公开网格）；</li>
 *   <li>他人网格 —— 只读视图（显示创建者名，不显示 accessId，服务端拒绝一切写操作）；</li>
 *   <li>本人网格 —— 管理主页（改名（两段式确认）/ 删除（两段式确认）/ 改密入口）
 *       与修改密码子页（旧 + 新 + 确认新，成功后自动回主页）。</li>
 * </ul>
 *
 * <p>输入保护：输入框聚焦时吞掉除 ESC 外的全部按键（AETextField 自带吞键 +
 * 本类兜底拦截），避免按 E/数字键/功能键误关 GUI 或切换快捷栏；点击输入框
 * 可视区域以外（含面板空白处）即取消输入焦点，恢复正常按键行为。</p>
 *
 * <p>身份信息（accessId/displayName/控制器位置/ownerName）由
 * {@link GridIdentityPayload} 服务端推送；accessId 仅 owner 可见。
 * 真正的权限校验在服务端，客户端表单只是入口。</p>
 */
public final class AccessScreen extends AbstractContainerScreen<AccessMenu> {

    private static final int PASSWORD_STATUS_NONE = 0;
    private static final int PASSWORD_STATUS_SET = 1;

    /** 管理视图子页：主页（信息展示 / 改名 / 删除 / 改密入口）。 */
    private static final int PAGE_MAIN = 0;
    /** 管理视图子页：修改密码页（旧 + 新 + 确认新；纯客户端导航，不改服务端视图）。 */
    private static final int PAGE_PASSWORD = 1;

    // ==================== AE2 调色板（取自 ae2:screens/common/palette.json） ====================

    /** 正文文字（AE2 DEFAULT_TEXT_COLOR）。 */
    private static final int COLOR_TEXT = 0xFF413F54;
    /** 次要文字 / 标签（AE2 MUTED_TEXT_COLOR）。 */
    private static final int COLOR_MUTED = 0xFF878FA5;
    /** 错误提示（AE2 ERROR）。 */
    private static final int COLOR_ERROR = 0xFFCE2401;

    /** 面板内容宽度（imageWidth 240 - 左右各 8px 边距）。 */
    private static final int CONTENT_WIDTH = 224;

    /**
     * AE2 控件样式：用 AE2 官方 {@code ScreenStyle.GSON} 反序列化一份仅含调色板的
     * 最小 ScreenStyle（键名对齐 {@code PaletteColor} 枚举与 {@code palette.json}），
     * 供 {@link AETextField} 构造取色（文字/选区/占位符颜色）。
     */
    private static final ScreenStyle AE2_STYLE = ScreenStyle.GSON.fromJson("""
            {
              "palette": {
                "DEFAULT_TEXT_COLOR": "#413f54",
                "MUTED_TEXT_COLOR": "#878fa5",
                "SELECTION_COLOR": "#ace9ff",
                "TEXTFIELD_PLACEHOLDER": "#dedfe3",
                "TEXTFIELD_SELECTION": "#FF0000FF",
                "TEXTFIELD_ERROR": "#FF1900",
                "TEXTFIELD_TEXT": "#f2f2f2",
                "ERROR": "#CE2401"
              }
            }""", ScreenStyle.class);

    /** AE2 通用面板背景纹理（256×256：1px 深色描边 + 内侧高光/阴影线，中段纯色）。 */
    private static final Blitter PANEL = Blitter.texture(
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/background.png"), 256, 256);

    // ==================== 控件（按视图/子页创建） ====================

    private AETextField accountPasswordBox;
    private AETextField accountConfirmBox;
    private AETextField gridNameBox;
    private AETextField gridPasswordBox;
    private AETextField renameBox;
    private AE2Button renameButton;
    private AETextField oldPasswordBox;
    private AETextField newPasswordBox;
    private AETextField confirmNewBox;
    private AE2Button deleteButton;

    /** 当前已构建控件对应的视图（-1 = 尚未构建；视图变化时重建）。 */
    private int builtView = -1;
    /** 管理视图子页（{@link #PAGE_MAIN} / {@link #PAGE_PASSWORD}）。 */
    private int managePage = PAGE_MAIN;
    /** 删除按钮的两段式确认状态：第一次点击仅置位，再次点击才发送。 */
    private boolean deleteArmed = false;
    /** 改名按钮的两段式确认状态（与删除同一模式）。 */
    private boolean renameArmed = false;
    /** 客户端本地校验/操作提示；空串 = 不显示。 */
    private String localHint = "";
    /** 提示样式：true = 错误（红），false = 普通（深色）。 */
    private boolean hintIsError = true;
    /** 上一帧收到的改密反馈打包值（(seq<<2)|code，边沿检测用；0 = 初始）。 */
    private int lastPackedPasswordStatus = 0;

    public AccessScreen(AccessMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 240;
        this.imageHeight = 120;
    }

    /** 各视图的面板高度（管理视图按子页区分；init 时设定，super.init() 再据此计算 topPos）。 */
    private int heightOf(int view) {
        return switch (view) {
            case AccessMenu.VIEW_REGISTER_ACCOUNT, AccessMenu.VIEW_REGISTER_GRID -> 132;
            case AccessMenu.VIEW_READONLY -> 96;
            case AccessMenu.VIEW_MANAGE -> this.managePage == PAGE_PASSWORD ? 168 : 196;
            default -> 48; // VIEW_NO_CONTROLLER
        };
    }

    /** 初始化 GUI 尺寸对应的坐标，并按当前视图/子页添加控件。 */
    @Override
    protected void init() {
        int view = this.menu.getStatusData().get(AccessMenu.DATA_VIEW);
        this.builtView = view;
        this.imageHeight = heightOf(view);
        super.init();
        switch (view) {
            case AccessMenu.VIEW_REGISTER_ACCOUNT -> initAccountRegisterWidgets();
            case AccessMenu.VIEW_REGISTER_GRID -> initGridRegisterWidgets();
            case AccessMenu.VIEW_MANAGE -> {
                if (this.managePage == PAGE_PASSWORD) {
                    initPasswordWidgets();
                } else {
                    initManageWidgets();
                }
            }
            default -> {
                // VIEW_NO_CONTROLLER / VIEW_READONLY：无表单控件
            }
        }
    }

    /** 无账号视图：账号注册表单（密码 + 确认，确认仅客户端比对）。 */
    private void initAccountRegisterWidgets() {
        this.accountPasswordBox = addMaskedField(38);
        this.accountConfirmBox = addMaskedField(72);
        this.addRenderableWidget(new AE2Button(this.leftPos + 70, this.topPos + 92, 100, 20,
                Component.translatable("gui.ae2webmanagement.button.register_account"), b -> onRegisterAccount()));
    }

    /** 未注册网格视图：名称 + 密码表单（都可空 = 默认名 + 公开网格）。 */
    private void initGridRegisterWidgets() {
        this.gridNameBox = addPlainField(38, AccessMenu.MAX_DISPLAY_NAME_LENGTH);
        this.gridPasswordBox = addMaskedField(72);
        this.addRenderableWidget(new AE2Button(this.leftPos + 70, this.topPos + 92, 100, 20,
                Component.translatable("gui.ae2webmanagement.button.register"), b -> onRegisterGrid()));
    }

    /** 本人网格主页：改名区（两段式确认）+ 删除按钮（两段式确认）+ 修改密码入口。 */
    private void initManageWidgets() {
        this.renameBox = addPlainField(112, AccessMenu.MAX_DISPLAY_NAME_LENGTH);
        this.renameButton = new AE2Button(this.leftPos + 8, this.topPos + 134, 106, 20,
                Component.translatable("gui.ae2webmanagement.button.rename"), b -> onRename());
        this.addRenderableWidget(this.renameButton);
        this.deleteButton = new AE2Button(this.leftPos + 126, this.topPos + 134, 106, 20,
                Component.translatable("gui.ae2webmanagement.button.delete"), b -> onDelete());
        this.addRenderableWidget(this.deleteButton);
        this.addRenderableWidget(new AE2Button(this.leftPos + 8, this.topPos + 160, 224, 20,
                Component.translatable("gui.ae2webmanagement.button.change_password"), b -> onOpenPasswordPage()));
    }

    /** 修改密码子页：旧密码 + 新密码 + 确认新密码 + 确认修改/返回按钮。 */
    private void initPasswordWidgets() {
        this.oldPasswordBox = addMaskedField(38);
        this.newPasswordBox = addMaskedField(72);
        this.confirmNewBox = addMaskedField(106);
        this.addRenderableWidget(new AE2Button(this.leftPos + 8, this.topPos + 128, 106, 20,
                Component.translatable("gui.ae2webmanagement.password.confirm_button"), b -> onChangePassword()));
        this.addRenderableWidget(new AE2Button(this.leftPos + 126, this.topPos + 128, 106, 20,
                Component.translatable("gui.ae2webmanagement.button.back"), b -> onBackToManage()));
    }

    /**
     * 添加 AE2 风格输入框：可视区域为 (leftPos + 8, topPos + y) 起、
     * 宽 {@value #CONTENT_WIDTH}、高 16 的矩形。
     *
     * <p>AETextField 的可视边界比构造参数外扩（左/上 2px、右含光标宽度补偿、
     * 下 2px），构造参数按此换算以对齐面板布局。</p>
     *
     * @param y 可视区域相对面板顶部的 y 坐标
     * @return 新建的 {@link AETextField}
     */
    private AETextField addField(int y) {
        AETextField box = new AETextField(AE2_STYLE, this.font,
                this.leftPos + 10, this.topPos + y + 2,
                CONTENT_WIDTH + 2 - this.font.width("_"), 18);
        this.addRenderableWidget(box);
        return box;
    }

    /** 添加普通文本输入框（显示原文），并按参数限制最大长度。 */
    private AETextField addPlainField(int y, int maxLength) {
        AETextField box = addField(y);
        box.setMaxLength(maxLength);
        return box;
    }

    /** 添加掩码密码输入框（输入内容以圆点呈现，避免旁观者看到明文）。 */
    private AETextField addMaskedField(int y) {
        AETextField box = addField(y);
        box.setMaxLength(GridPasswords.MAX_PASSWORD_LENGTH);
        box.setFormatter((value, index) -> Component.literal("•".repeat(value.length())).getVisualOrderText());
        return box;
    }

    // ==================== 输入保护（E/功能键不再误触 GUI 行为） ====================

    /**
     * 输入态兜底拦截：任一输入框聚焦时，除 ESC（仍直接关 GUI）外的全部按键
     * 就地吞掉——导航键/退格/粘贴等已由 {@link AETextField#keyPressed} 处理，
     * 其余按键（关 GUI 的 E、快捷栏数字、功能键等）不再冒泡到容器逻辑。
     * 字符输入走 {@code charTyped}，不受影响。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.getFocused() instanceof AETextField && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            this.getFocused().keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 点击输入框可视区域以外（含面板空白与面板外）时先取消输入焦点：
     * 焦点解除后恢复正常按键行为（如按 E 关闭 GUI）。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.getFocused() instanceof AETextField field && !field.isMouseOver(mouseX, mouseY)) {
            this.setFocused(null);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ==================== 渲染 ====================

    /** 先绘制普通容器内容，再绘制身份信息、状态与视图专属文字。 */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 视图变化（账号注册成功 / 网格注册或删除 / 权限分层变化）→ 重建控件，无需重开 GUI
        int view = this.menu.getStatusData().get(AccessMenu.DATA_VIEW);
        if (view != this.builtView) {
            this.deleteArmed = false;
            this.renameArmed = false;
            this.managePage = PAGE_MAIN;
            this.localHint = "";
            this.lastPackedPasswordStatus = this.menu.getStatusData().get(AccessMenu.DATA_PASSWORD_STATUS);
            rebuildWidgets();
        }
        // 改密反馈边沿检测（打包值变化 = 服务端完成了一次新的改密尝试）
        handlePasswordFeedback(view);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderDynamicText(guiGraphics, view);
    }

    /**
     * 改密反馈处理：打包值 (seq<<2)|code 变化且当前在修改密码子页时给出反馈——
     * 成功自动回到管理主页（「完成后回到改名页面」），失败留在子页显示错误。
     */
    private void handlePasswordFeedback(int view) {
        int packed = this.menu.getStatusData().get(AccessMenu.DATA_PASSWORD_STATUS);
        if (packed == this.lastPackedPasswordStatus) {
            return;
        }
        this.lastPackedPasswordStatus = packed;
        if (view != AccessMenu.VIEW_MANAGE || this.managePage != PAGE_PASSWORD) {
            return;
        }
        switch (packed & 0x3) {
            case AccessMenu.PASSWORD_CHANGE_SUCCESS -> {
                this.localHint = translate("gui.ae2webmanagement.password.changed");
                this.hintIsError = false;
                this.managePage = PAGE_MAIN;
                rebuildWidgets();
            }
            case AccessMenu.PASSWORD_CHANGE_OLD_MISMATCH -> {
                this.localHint = translate("gui.ae2webmanagement.password.old_mismatch");
                this.hintIsError = true;
            }
            case AccessMenu.PASSWORD_CHANGE_INVALID -> {
                this.localHint = translate("gui.ae2webmanagement.password.invalid_new");
                this.hintIsError = true;
            }
            default -> {
                // IDLE：无反馈
            }
        }
    }

    /** 九宫格绘制 AE2 面板背景：四角与上下边缘 1:1 取自纹理，中段从纯色内区拉伸。 */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;
        // 纹理结构（逐像素采样测得）：上/左/右边缘 2px（1px 描边 + 1px 高光），
        // 底部边缘 4px（内部 + 阴影线 + 描边），中段为纯色 #CBCCD4。
        final int edge = 2;
        final int bottom = 4;
        final int texW = 256;
        final int texH = 256;
        int innerW = w - edge * 2;
        int innerH = h - edge - bottom;
        // 四角
        panel().src(0, 0, edge, edge).dest(x, y, edge, edge).blit(guiGraphics);
        panel().src(texW - edge, 0, edge, edge).dest(x + w - edge, y, edge, edge).blit(guiGraphics);
        panel().src(0, texH - bottom, edge, bottom).dest(x, y + h - bottom, edge, bottom).blit(guiGraphics);
        panel().src(texW - edge, texH - bottom, edge, bottom).dest(x + w - edge, y + h - bottom, edge, bottom).blit(guiGraphics);
        // 上下边缘（横向拉伸）
        panel().src(edge, 0, texW - edge * 2, edge).dest(x + edge, y, innerW, edge).blit(guiGraphics);
        panel().src(edge, texH - bottom, texW - edge * 2, bottom).dest(x + edge, y + h - bottom, innerW, bottom).blit(guiGraphics);
        // 左右边缘（纵向拉伸，取样自纯色内区 1px 高）
        panel().src(0, 8, edge, 1).dest(x, y + edge, edge, innerH).blit(guiGraphics);
        panel().src(texW - edge, 8, edge, 1).dest(x + w - edge, y + edge, edge, innerH).blit(guiGraphics);
        // 中段（纯色内区拉伸）
        panel().src(edge, 8, texW - edge * 2, 1).dest(x + edge, y + edge, innerW, innerH).blit(guiGraphics);
    }

    /** 取一份可变的 Blitter 副本（Blitter 的 src/dest 链式调用会修改自身状态）。 */
    private static Blitter panel() {
        return PANEL.copy();
    }

    /** 在面板顶部左上角（AE2 机器界面惯例位置）绘制标题；管理视图改密子页显示子页标题。 */
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int view = this.menu.getStatusData().get(AccessMenu.DATA_VIEW);
        boolean onPasswordPage = view == AccessMenu.VIEW_MANAGE && this.managePage == PAGE_PASSWORD;
        Component title = onPasswordPage
                ? Component.translatable("gui.ae2webmanagement.password.title")
                : this.title;
        guiGraphics.drawString(this.font, title, 8, 6, COLOR_TEXT, false);
    }

    /** 绘制身份信息、状态消息、分区小标题与客户端提示（视图/子页相关）。 */
    private void renderDynamicText(GuiGraphics guiGraphics, int view) {
        // 顶部状态消息（服务端同步的状态码 + 视图文案）
        Component status = getStatusMessage(view);
        if (status != null) {
            guiGraphics.drawString(this.font, status, this.leftPos + 8, this.topPos + 16, COLOR_MUTED, false);
        }

        switch (view) {
            case AccessMenu.VIEW_REGISTER_ACCOUNT -> {
                drawLabel(guiGraphics, 28, "gui.ae2webmanagement.account.password");
                drawLabel(guiGraphics, 62, "gui.ae2webmanagement.account.confirm");
            }
            case AccessMenu.VIEW_REGISTER_GRID -> {
                drawLabel(guiGraphics, 28, "gui.ae2webmanagement.rename.label");
                drawLabel(guiGraphics, 62, "gui.ae2webmanagement.password.label");
            }
            case AccessMenu.VIEW_READONLY -> drawIdentityBlock(guiGraphics, 28);
            case AccessMenu.VIEW_MANAGE -> {
                if (this.managePage == PAGE_PASSWORD) {
                    drawLabel(guiGraphics, 16, "gui.ae2webmanagement.password.confirm_hint");
                    drawLabel(guiGraphics, 28, "gui.ae2webmanagement.password.old");
                    drawLabel(guiGraphics, 62, "gui.ae2webmanagement.password.new");
                    drawLabel(guiGraphics, 96, "gui.ae2webmanagement.password.confirm_new");
                } else {
                    drawIdentityBlock(guiGraphics, 28);
                    drawLabel(guiGraphics, 102, "gui.ae2webmanagement.rename.label");
                    drawPasswordStatus(guiGraphics);
                }
            }
            default -> {
                // 无控制器视图：仅顶部状态消息
            }
        }

        // 客户端本地校验/操作提示（错误红色、普通深色），与密码状态行互斥
        if (!this.localHint.isEmpty()) {
            Component hint = Component.literal(this.localHint);
            int hintX = this.leftPos + (this.imageWidth - this.font.width(hint)) / 2;
            guiGraphics.drawString(this.font, hint, hintX, this.topPos + this.imageHeight - 14,
                    this.hintIsError ? COLOR_ERROR : COLOR_TEXT, false);
        }
    }

    /**
     * 绘制身份信息块（两列式：muted 标签 + 深色值；超长省略号截断）。
     *
     * <p>接入 ID 仅 owner 可见（只读视图不显示；未注册时不显示该行）。</p>
     *
     * @param guiGraphics 绘制目标
     * @param y           信息块起始 y（相对面板顶部）
     */
    private void drawIdentityBlock(GuiGraphics guiGraphics, int y) {
        GridIdentityPayload identity = this.menu.getClientIdentity();
        boolean registered = identity != null && !identity.accessId().isEmpty();
        String unregistered = translate("gui.ae2webmanagement.identity.unregistered");

        // 显示名
        drawInfoLine(guiGraphics, y, "gui.ae2webmanagement.identity.display_name",
                registered ? identity.displayName() : unregistered, !registered);
        y += 12;

        // 接入 ID（owner 专属，完整 UUID 独占一行以避免截断）
        if (registered && identity.isOwner()) {
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.ae2webmanagement.identity.access_id").getString() + ":",
                    this.leftPos + 8, this.topPos + y, COLOR_MUTED, false);
            guiGraphics.drawString(this.font, ellipsize(identity.accessId(), CONTENT_WIDTH),
                    this.leftPos + 8, this.topPos + y + 10, COLOR_TEXT, false);
            y += 22;
        }

        // 控制器位置（维度 + 坐标各占一行，避免截断）
        if (identity != null && !identity.dimension().isEmpty()) {
            drawInfoLine(guiGraphics, y, "gui.ae2webmanagement.identity.controller",
                    identity.dimension(), false);
            drawInfoLine(guiGraphics, y + 12, "gui.ae2webmanagement.identity.pos",
                    identity.pos().toShortString(), false);
            y += 24;
        }

        // 所有者
        if (registered) {
            String ownerName = identity.ownerName().isEmpty()
                    ? translate("gui.ae2webmanagement.identity.unowned")
                    : identity.ownerName();
            drawInfoLine(guiGraphics, y, "gui.ae2webmanagement.identity.owner", ownerName,
                    identity.ownerName().isEmpty());
        }
    }

    /** 绘制管理主页底部的网格密码状态行（无本地提示时才显示）。 */
    private void drawPasswordStatus(GuiGraphics guiGraphics) {
        if (!this.localHint.isEmpty()) {
            return;
        }
        int flag = this.menu.getStatusData().get(AccessMenu.DATA_PASSWORD_SET);
        Component message = flag == PASSWORD_STATUS_SET
                ? Component.translatable("gui.ae2webmanagement.status.password_set")
                : Component.translatable("gui.ae2webmanagement.status.password_none");
        guiGraphics.drawString(this.font, message, this.leftPos + 8, this.topPos + 182, COLOR_MUTED, false);
    }

    /** 在指定相对坐标绘制一行 muted 分区小标题。 */
    private void drawLabel(GuiGraphics guiGraphics, int y, String key) {
        guiGraphics.drawString(this.font, Component.translatable(key), this.leftPos + 8, this.topPos + y, COLOR_MUTED, false);
    }

    /** 绘制一行「muted 标签: 值」信息；值超长时省略号截断。 */
    private void drawInfoLine(GuiGraphics guiGraphics, int y, String labelKey, String value, boolean muted) {
        String label = Component.translatable(labelKey).getString() + ": ";
        int labelWidth = this.font.width(label);
        guiGraphics.drawString(this.font, label, this.leftPos + 8, this.topPos + y, COLOR_MUTED, false);
        guiGraphics.drawString(this.font, ellipsize(value, CONTENT_WIDTH - labelWidth),
                this.leftPos + 8 + labelWidth, this.topPos + y, muted ? COLOR_MUTED : COLOR_TEXT, false);
    }

    // ==================== 按钮 → 客户端校验 + 发包 ====================

    /** 提交账号注册：客户端比对两次密码一致且非空后发送（服务端只收一个密码）。 */
    private void onRegisterAccount() {
        String password = valueOf(this.accountPasswordBox);
        String confirm = valueOf(this.accountConfirmBox);
        if (password.isEmpty() || confirm.isEmpty()) {
            this.localHint = translate("gui.ae2webmanagement.account.empty");
            this.hintIsError = true;
            return;
        }
        if (!password.equals(confirm)) {
            this.localHint = translate("gui.ae2webmanagement.account.mismatch");
            this.hintIsError = true;
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

    /** 改名（两段式确认）：第一次点击置位并改按钮文案，再次点击才发送。 */
    private void onRename() {
        if (!this.renameArmed) {
            String name = valueOf(this.renameBox);
            if (name.isBlank()) {
                return;
            }
            this.renameArmed = true;
            this.renameButton.setMessage(Component.translatable("gui.ae2webmanagement.button.rename_confirm"));
            this.localHint = translate("gui.ae2webmanagement.rename.hint");
            this.hintIsError = true;
            return;
        }
        this.renameArmed = false;
        this.renameButton.setMessage(Component.translatable("gui.ae2webmanagement.button.rename"));
        this.localHint = "";
        PacketDistributor.sendToServer(new GridRenamePayload(this.menu.containerId, valueOf(this.renameBox).trim()));
        this.renameBox.setValue("");
    }

    /** 进入修改密码子页（记录当前改密反馈打包值，避免旧反馈误触发）。 */
    private void onOpenPasswordPage() {
        this.managePage = PAGE_PASSWORD;
        this.localHint = "";
        this.lastPackedPasswordStatus = this.menu.getStatusData().get(AccessMenu.DATA_PASSWORD_STATUS);
        rebuildWidgets();
    }

    /** 从修改密码子页返回管理主页。 */
    private void onBackToManage() {
        this.managePage = PAGE_MAIN;
        this.localHint = "";
        rebuildWidgets();
    }

    /** 提交改密：客户端先比对两次新密码一致，再连同旧密码发给服务端校验。 */
    private void onChangePassword() {
        String oldPassword = valueOf(this.oldPasswordBox);
        String newPassword = valueOf(this.newPasswordBox);
        String confirmNew = valueOf(this.confirmNewBox);
        if (!newPassword.equals(confirmNew)) {
            this.localHint = translate("gui.ae2webmanagement.password.mismatch");
            this.hintIsError = true;
            return;
        }
        this.localHint = "";
        PacketDistributor.sendToServer(
                new GridChangePasswordPayload(this.menu.containerId, oldPassword, newPassword));
        this.oldPasswordBox.setValue("");
        this.newPasswordBox.setValue("");
        this.confirmNewBox.setValue("");
    }

    /** 删除网格：两段式确认（第一次点击置位并改按钮文案，再次点击才发送）。 */
    private void onDelete() {
        if (!this.deleteArmed) {
            this.deleteArmed = true;
            this.deleteButton.setMessage(Component.translatable("gui.ae2webmanagement.button.delete_confirm"));
            this.localHint = translate("gui.ae2webmanagement.delete.hint");
            this.hintIsError = true;
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

    // ==================== 工具 ====================

    /** 读取输入框内容（控件未创建时为空串）。 */
    private static String valueOf(AETextField box) {
        return box == null ? "" : box.getValue();
    }

    /** 按宽度省略号截断文本，避免超长字符串（如 UUID、维度 ID）溢出面板。 */
    private String ellipsize(String value, int maxWidth) {
        if (value == null || this.font.width(value) <= maxWidth) {
            return value == null ? "" : value;
        }
        return this.font.plainSubstrByWidth(value, Math.max(0, maxWidth - this.font.width("..."))) + "...";
    }

    /** 取本地化文本的原始字符串（用于拼进普通文本行）。 */
    private String translate(String key) {
        return Component.translatable(key).getString();
    }
}
