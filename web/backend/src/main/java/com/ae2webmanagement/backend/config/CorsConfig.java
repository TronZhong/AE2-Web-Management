package com.ae2webmanagement.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置，允许前端开发服务器访问后端 API。
 *
 * <p>{@code @EnableConfigurationProperties} 使本配置自给自足：即使只在
 * {@code @WebMvcTest} 切片上下文中加载（主类的 {@code @ConfigurationPropertiesScan}
 * 不生效），{@link AppProperties} 也能被注册并绑定 {@code app.*}。</p>
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class CorsConfig implements WebMvcConfigurer {

	private final AppProperties properties;

	public CorsConfig(AppProperties properties) {
		this.properties = properties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
			.allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
			.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
			.allowedHeaders("*");
	}
}