package com.ae2webmanagement.ae2webmanagementmod.client.icon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import com.ae2webmanagement.ae2webmanagementmod.AE2WebManagement;
import com.ae2webmanagement.ae2webmanagementmod.Config;
import com.ae2webmanagement.ae2webmanagementmod.icon.IconExporter;
import com.ae2webmanagement.ae2webmanagementmod.icon.IconPaths;
import com.ae2webmanagement.ae2webmanagementmod.snapshot.Snapshot;

/**
 * 客户端物品/流体图标离屏渲染服务（仅客户端加载）。
 *
 * <p>对应 docs/roadmap.md §5 的「模组端：IconService（客户端专用，性能核心）」：
 * <ul>
 *   <li><b>作业队列 + 每帧预算</b>：HTTP 线程/快照观察只把 AEKey 的 NBT 入队
 *       （{@link ConcurrentLinkedQueue}，不碰 Minecraft 状态）；渲染线程在每帧
 *       渲染完成后（{@code RenderFrameEvent.Post}，实现时已验证的挂点）每帧最多
 *       消费 {@code iconRenderPerFrame} 个，任意规模网络都只渐进补齐、不掉帧。</li>
 *   <li><b>复用单个 FBO</b>：64×64 {@link TextureTarget} 全程复用，绑定后借
 *       {@code ItemRenderer}（经 {@link GuiGraphics}）画完，读回像素并立即恢复
 *       默认 framebuffer 与 RenderSystem 状态，不污染下一帧。</li>
 *   <li><b>去重</b>：内存 {@code Set<String>}（已入队）+ 磁盘存在性检查（已渲染），
 *       已存在的 PNG 永不重渲染。</li>
 *   <li><b>IO 下移</b>：读回像素后的 PNG 编码与文件写入交给单线程 daemon executor，
 *       渲染线程不碰磁盘。</li>
 *   <li><b>快照 key 来源</b>：单人模式（integrated server）下直接读
 *       {@code AE2WebManagement.snapshotManager()} 的线程安全缓存，观察节流
 *       （每 20 帧一次）只 diff 新增 key。</li>
 * </ul>
 * 专用服务器不加载本类，无客户端渲染、零开销。
 */
@OnlyIn(Dist.CLIENT)
public final class IconService implements IconExporter {

    /** 导出图标边长（像素）。 */
    public static final int ICON_SIZE = 64;

    /** 每多少帧观察一次快照缓存，把新增 key 入队（对应 roadmap 的「每 40 tick」）。 */
    private static final int OBSERVE_EVERY_FRAMES = 40;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 图标缓存根目录（{@code config/ae2webmanagement/icons}）。 */
    private final Path iconDir;
    /** 待渲染作业队列（任意线程写入 / 渲染线程消费），元素是 AEKey 通用 NBT 的副本。 */
    private final ConcurrentLinkedQueue<CompoundTag> queue = new ConcurrentLinkedQueue<>();
    /** 已入队（或已渲染）的 key 集合，按 {@code namespace:path} 去重。 */
    private final Set<String> queuedKeys = ConcurrentHashMap.newKeySet();
    /** PNG 编码 + 文件写入专用线程（渲染线程不碰磁盘）。 */
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ae2web-icons-io");
        thread.setDaemon(true);
        return thread;
    });

    /** 复用的离屏画布（仅渲染线程访问）。 */
    private TextureTarget fbo;
    /** 快照观察节流计数（仅渲染线程访问）。 */
    private int frameCounter;

    /** 构造时不做任何 Minecraft 状态操作；目录懒创建于首次写入。 */
    public IconService() {
        this.iconDir = IconPaths.root();
    }

    /**
     * 请求后台渲染一个 AEKey（任意线程可调用）。
     *
     * <p>只把 key 的 NBT 副本入队，不碰 Minecraft/AE2 状态；去重与磁盘检查
     * 均在本方法内完成，保证已存在的 PNG 永不重渲染。</p>
     *
     * @param keyNbt AEKey 通用 NBT（形如 {@code {"#t":"ae2:i","id":"minecraft:iron_block"}}）
     */
    @Override
    public void request(CompoundTag keyNbt) {
        if (!Config.ICON_EXPORT_ENABLED.get()) {
            return;
        }
        // 只导出物品（ae2:i）与流体（ae2:f）两种 AEKey 类型，其余交给前端占位
        String type = keyNbt.getString("#t");
        if (!"ae2:i".equals(type) && !"ae2:f".equals(type)) {
            return;
        }
        String id = keyNbt.getString("id");
        if (id.isEmpty()) {
            return;
        }
        ResourceLocation registryId = ResourceLocation.tryParse(id);
        if (registryId == null || registryId.getPath().isEmpty()) {
            return;
        }
        String cacheId = registryId.getNamespace() + ":" + registryId.getPath();
        if (!this.queuedKeys.add(cacheId)) {
            return; // 已入队或已渲染，跳过
        }
        Path target = this.iconDir.resolve(registryId.getNamespace()).resolve(registryId.getPath() + ".png");
        if (Files.isRegularFile(target)) {
            return; // 磁盘上已存在，永不重渲染
        }
        this.queue.add(keyNbt.copy());
    }

    /**
     * 每帧渲染完成后消费图标队列（由客户端 {@code AE2WebManagementClient} 在
     * {@code RenderFrameEvent.Post} 调用，渲染线程）。
     *
     * <p>先按节流周期观察快照缓存把新增 key 入队，再按每帧预算消费队列；
     * 未进入世界（主菜单）时不做任何渲染。</p>
     */
    public void renderTick() {
        if (!Config.ICON_EXPORT_ENABLED.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return; // 无客户端世界，无法解析注册表，也无需导出
        }
        if (++this.frameCounter >= OBSERVE_EVERY_FRAMES) {
            this.frameCounter = 0;
            observeSnapshots();
        }
        if (this.queue.isEmpty()) {
            return;
        }
        int budget = Math.max(1, Config.ICON_RENDER_PER_FRAME.get());
        int rendered = 0;
        while (rendered < budget) {
            CompoundTag nbt = this.queue.poll();
            if (nbt == null) {
                break;
            }
            rendered++;
            try {
                renderOne(minecraft, nbt);
            } catch (Throwable throwable) {
                LOGGER.warn("[AE2Web] Failed to render icon for {}: {}", nbt, throwable.toString());
            }
        }
    }

    /**
     * 观察快照缓存：把库存与样板书里出现过的 key 全部入队（内部去重）。
     *
     * <p>单人模式（integrated server）下 {@code SnapshotManager} 是同一 JVM 的
     * 共享线程安全缓存，渲染线程直接读；只对新增 key 生效，不每 4 秒全量重扫。</p>
     */
    private void observeSnapshots() {
        for (Snapshot snapshot : AE2WebManagement.snapshotManager().getAll()) {
            for (Snapshot.StackEntry entry : snapshot.inventory()) {
                this.request(entry.key());
            }
            for (Snapshot.PatternEntry pattern : snapshot.patterns()) {
                this.request(pattern.primaryOutput().key());
                for (Snapshot.StackEntry output : pattern.outputs()) {
                    this.request(output.key());
                }
                for (Snapshot.PatternEntry.PatternInputEntry input : pattern.inputs()) {
                    for (Snapshot.StackEntry possible : input.possibleInputs()) {
                        this.request(possible.key());
                    }
                }
            }
        }
    }

    /**
     * 解析一个待渲染 key 并离屏渲染为 PNG。
     *
     * <p>在渲染线程把 NBT 反序列化为 AEKey，物品借 {@code ItemRenderer}（经
     * {@link GuiGraphics}）渲染真实物品栏 3D 图标，流体走
     * {@code FluidType.getStillTexture()} 贴图 blit 路径；其他 AEKey 类型跳过
     * （前端占位）。渲染完成后像素读回交给 IO 线程写盘。</p>
     *
     * @param minecraft 客户端实例（level 非空）
     * @param nbt       AEKey 通用 NBT
     */
    private void renderOne(Minecraft minecraft, CompoundTag nbt) {
        HolderLookup.Provider registries = minecraft.level.registryAccess();
        AEKey key = AEKey.fromTagGeneric(registries, nbt);
        if (key == null) {
            return;
        }

        ResourceLocation registryId;
        if (key instanceof AEItemKey itemKey) {
            registryId = BuiltInRegistries.ITEM.getKey(itemKey.getItem());
        } else if (key instanceof AEFluidKey fluidKey) {
            registryId = BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
        } else {
            return; // 其他 AEKey 类型不导出图标
        }
        if (registryId == null || registryId.getPath().isEmpty()) {
            return;
        }

        NativeImage image;
        if (key instanceof AEItemKey itemKey) {
            image = renderToImage(minecraft, gfx -> gfx.renderFakeItem(itemKey.toStack(), 0, 0));
        } else if (key instanceof AEFluidKey fluidKey) {
            image = renderToImage(minecraft, gfx -> renderFluid(gfx, fluidKey.getFluid()));
        } else {
            return;
        }
        if (image == null) {
            return;
        }

        Path target = this.iconDir.resolve(registryId.getNamespace()).resolve(registryId.getPath() + ".png");
        this.ioExecutor.execute(() -> writeIcon(image, target));
    }

    /**
     * 在复用 FBO 上执行一次离屏绘制并读回像素。
     *
     * <p>完整复刻游戏 GUI 的投影与模型视图设置（GUI ortho + Z 偏移），保证
     * 图标与物品栏观感一致；绘制完成后立即恢复默认 framebuffer 与
     * RenderSystem 状态（projection / model view / blend），不污染下一帧。</p>
     *
     * @param minecraft 客户端实例
     * @param draw      实际绘制动作（写入 16×16 画布）
     * @return 读回的像素；绘制失败（异常被上层捕获）或无法读回时为 null
     */
    private NativeImage renderToImage(Minecraft minecraft, Consumer<GuiGraphics> draw) {
        ensureFbo();

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0F, ICON_SIZE, ICON_SIZE, 0.0F, 1000.0F, 21000.0F),
                VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0.0F, 0.0F, -11000.0F);
        RenderSystem.applyModelViewMatrix();

        this.fbo.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.fbo.clear(false);
        this.fbo.bindWrite(true);

        NativeImage image = null;
        try {
            GuiGraphics gfx = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            draw.accept(gfx);
            gfx.flush();

            this.fbo.bindRead();
            image = new NativeImage(ICON_SIZE, ICON_SIZE, true);
            image.downloadTexture(0, false);
            this.fbo.unbindRead();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        if (image != null) {
            flipVertically(image); // glGetTexImage 返回的纹理行序是自底向上的，需垂直翻转
        }
        return image;
    }

    /**
     * 流体图标：按 {@code IClientFluidTypeExtensions.getStillTexture()} 贴图
     * blit 整幅 16×16，并施加流体的 {@code getTintColor()} 染色。
     *
     * @param gfx   离屏绘制上下文
     * @param fluid 要绘制的流体
     */
    private static void renderFluid(GuiGraphics gfx, Fluid fluid) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation still = extensions.getStillTexture();
        if (still == null) {
            return; // 无贴图（如 EMPTY），跳过，前端占位
        }
        int tint = extensions.getTintColor();
        float alpha = FastColor.ARGB32.alpha(tint) / 255.0F;
        float red = FastColor.ARGB32.red(tint) / 255.0F;
        float green = FastColor.ARGB32.green(tint) / 255.0F;
        float blue = FastColor.ARGB32.blue(tint) / 255.0F;
        gfx.setColor(red, green, blue, alpha);
        gfx.blit(still, 0, 0, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, 16, 16);
        gfx.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 惰性创建（或失效后重建）复用的离屏画布；仅渲染线程调用。 */
    private void ensureFbo() {
        if (this.fbo == null || this.fbo.frameBufferId < 0) {
            this.fbo = new TextureTarget(ICON_SIZE, ICON_SIZE, true, true);
        }
    }

    /**
     * 在 IO 线程把像素写为 PNG 文件（{@code config/ae2webmanagement/icons/<ns>/<path>.png}）。
     *
     * @param image  已读回的像素（由本方法负责关闭）
     * @param target 目标文件路径
     */
    private void writeIcon(NativeImage image, Path target) {
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            image.writeToFile(target);
        } catch (Exception exception) {
            LOGGER.warn("[AE2Web] Failed to write icon to {}: {}", target, exception.toString());
        } finally {
            image.close();
        }
    }

    /** 垂直翻转图像行（glGetTexImage 返回自底向上的行序）。 */
    private static void flipVertically(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height / 2; y++) {
            int opposite = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int top = image.getPixelRGBA(x, y);
                int bottom = image.getPixelRGBA(x, opposite);
                image.setPixelRGBA(x, y, bottom);
                image.setPixelRGBA(x, opposite, top);
            }
        }
    }
}
