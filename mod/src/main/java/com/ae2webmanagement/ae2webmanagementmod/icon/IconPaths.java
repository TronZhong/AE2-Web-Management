package com.ae2webmanagement.ae2webmanagementmod.icon;

import java.nio.file.Path;

import net.neoforged.fml.loading.FMLPaths;

import com.ae2webmanagement.ae2webmanagementmod.AE2WebManagement;

/**
 * 图标缓存目录（模组公共侧，服务端与客户端共用）。
 *
 * <p>图标导出是「渲染（客户端）与静态文件服务（HTTP 层）两条独立路径」：
 * 客户端 {@code IconService} 把 {@link #ICON_RESOLUTION}×{@link #ICON_RESOLUTION}
 * PNG 写入 {@code config/ae2webmanagement/icons/<分辨率>/}，本地 API 的
 * {@code GET /icons/...} 只读这个目录（docs/roadmap.md §5）。
 * 分辨率编入缓存路径：调整分辨率后旧目录自然失活（新目录为空 → 404 惰性补齐
 * 触发重渲染），无需手动清理缓存。专用服务器无客户端时该目录通常为空，
 * 图标端点一律 404。</p>
 */
public final class IconPaths {

    /** 导出图标边长（像素）；变更本值即切换缓存子目录，旧分辨率缓存自动失效。 */
    public static final int ICON_RESOLUTION = 64;

    private IconPaths() {
    }

    /** @return 当前分辨率的图标缓存根目录（{@code config/ae2webmanagement/icons/<边长>x<边长>}） */
    public static Path root() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(AE2WebManagement.MODID)
                .resolve("icons")
                .resolve(ICON_RESOLUTION + "x" + ICON_RESOLUTION);
    }
}
