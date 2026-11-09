package com.ae2webmanagement.backend.controller;

import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ae2webmanagement.backend.client.ModApiClient;
import com.ae2webmanagement.backend.client.ModApiException;

/**
 * 物品图标转发端点（契约 v0.7，docs/contracts/web-api.md「图标转发端点」）：
 * {@code GET /api/icons/{namespace}/{path}.png} —— 对模组
 * {@code GET /icons/{namespace}/{path}.png}（模组 0.0.10 已就绪）做纯字节透传，
 * 透传 {@code Content-Type: image/png} 与 {@code Cache-Control: public, max-age=86400}。
 *
 * <p>设计要点（docs/roadmap.md §5）：</p>
 * <ul>
 *   <li>纯代理：不解析图标的 JSON、不做后端自建缓存，浏览器缓存承担主要去重；</li>
 *   <li>不做 per-network 访问控制：图标为全局静态资源，仅要求登录
 *       （Sa-Token 拦截 {@code /api/**}）；</li>
 *   <li>转发前先做与模组一致的路径字符集校验（{@code [a-z0-9_.-]}），
 *       非法路径直接 {@code 400 invalid_icon_path}，不触达模组；
 *       模组侧另有 normalize + 前缀校验兜底路径穿越；</li>
 *   <li>模组不可达 / 共享密钥不匹配等错误由 {@code GlobalExceptionHandler}
 *       兜底翻译（{@code 503 mod_unreachable} / {@code mod_unavailable}），
 *       模组 404（图标未渲染）原样透传，前端 {@code @error} 回退占位方块并重试。</li>
 * </ul>
 *
 * <p>Controller 只做路径校验与字节转发，不含业务；经 {@link ModApiClient}
 * 访问模组（service 层不参与本端点）。</p>
 */
@RestController
@RequestMapping("/api")
public class IconController {

	/** 图标路径段字符集：与模组 {@code LocalDataServer.isValidIconSegment} 一致。 */
	private static final Pattern ICON_SEGMENT = Pattern.compile("[a-z0-9_.-]+");

	private final ModApiClient modApiClient;

	/**
	 * @param modApiClient 访问模组的唯一入口（图标以字节转发）
	 */
	public IconController(ModApiClient modApiClient) {
		this.modApiClient = modApiClient;
	}

	/**
	 * 图标转发（{@code GET /api/icons/{namespace}/{fileName}}）。
	 *
	 * <p>契约条目：docs/contracts/web-api.md「图标转发端点」（v0.7）。
	 * {@code fileName} 形如 {@code iron_block.png}，两段均只允许
	 * {@code [a-z0-9_.-]}；缺失文件由模组返回 404 {@code icon_not_found}
	 * 并惰性补齐，非法路径返回 400 {@code invalid_icon_path}。</p>
	 *
	 * @param namespace 图标命名空间段（快照 key 的 {@code id} 冒号前部分）
	 * @param fileName  图标文件名段（快照 key 的 {@code id} 冒号后部分 + {@code .png}）
	 * @return PNG 字节，响应头 {@code Content-Type: image/png} +
	 *         {@code Cache-Control: public, max-age=86400}（图标一经渲染即静态）
	 */
	@GetMapping("/icons/{namespace}/{fileName}")
	public ResponseEntity<byte[]> icon(@PathVariable String namespace, @PathVariable String fileName) {
		if (!isValidIconSegment(namespace) || !isValidIconFileName(fileName)) {
			throw new ModApiException(400, "invalid_icon_path");
		}
		byte[] bytes = this.modApiClient.fetchIcon(namespace, fileName);
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic())
			.body(bytes);
	}

	/**
	 * 校验图标路径段：仅允许 {@code [a-z0-9_.-]} 且不得为相对路径段
	 * {@code .} / {@code ..}（杜绝转发层面的路径穿越形态）。
	 *
	 * @param segment 路径段原值
	 * @return 是否合法
	 */
	private static boolean isValidIconSegment(String segment) {
		return ICON_SEGMENT.matcher(segment).matches() && !".".equals(segment) && !"..".equals(segment);
	}

	/**
	 * 校验图标文件名段：须以 {@code .png} 结尾，去掉扩展名后仍为合法路径段。
	 *
	 * @param fileName 文件名段原值（如 {@code iron_block.png}）
	 * @return 是否合法
	 */
	private static boolean isValidIconFileName(String fileName) {
		if (!fileName.endsWith(".png")) {
			return false;
		}
		String path = fileName.substring(0, fileName.length() - ".png".length());
		return !path.isEmpty() && isValidIconSegment(path);
	}
}
