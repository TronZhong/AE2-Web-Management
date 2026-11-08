package com.ae2webmanagement.ae2webmanagementmod.icon;

import java.nio.file.Path;

import net.neoforged.fml.loading.FMLPaths;

import com.ae2webmanagement.ae2webmanagementmod.AE2WebManagement;

/**
 * 图标缓存目录（模组公共侧，服务端与客户端共用）。
 *
 * <p>图标导出是「渲染（客户端）与静态文件服务（HTTP 层）两条独立路径」：
 * 客户端 {@code IconService} 把 16×16 PNG 写入 {@code config/ae2webmanagement/icons/}，
 * 本地 API 的 {@code GET /icons/...} 只读这个目录（docs/roadmap.md §5）。
 * 专用服务器无客户端时该目录通常为空，图标端点一律 404。</p>
 */
public final class IconPaths {

    private IconPaths() {
    }

    /** @return 图标缓存根目录（{@code config/ae2webmanagement/icons}） */
    public static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve(AE2WebManagement.MODID).resolve("icons");
    }
}
