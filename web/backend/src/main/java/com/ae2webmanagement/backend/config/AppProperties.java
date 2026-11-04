package com.ae2webmanagement.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用外部化配置，对应 {@code application.yaml} 中的 {@code app} 前缀。
 *
 * <p>仅承载与部署环境相关的配置；业务常量不应放在这里。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Mod mod, Cors cors, Auth auth) {

	/** 模组本地 API 连接配置。 */
	public record Mod(String baseUrl, String sharedSecret) {
	}

	/** 跨域访问配置。 */
	public record Cors(List<String> allowedOrigins) {
	}

	/**
	 * 账号认证配置：{@code enabled} 为 {@code false} 时关闭登录校验（仅测试切片）；
	 * {@code adminUsername} / {@code adminPassword} 仅在账户表为空的首次启动时引导建号。
	 */
	public record Auth(boolean enabled, String adminUsername, String adminPassword) {
	}
}