package com.ae2webmanagement.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;

/**
 * 账号认证拦截器（契约 v0.2「认证端点」）：拦截 {@code /api/**} 并强制已登录，
 * 仅放行 {@code POST /api/auth/login}；未登录由 Sa-Token 抛 {@code NotLoginException}，
 * 经 {@code GlobalExceptionHandler} 输出 {@code 401 {"error":"unauthorized"}}。
 *
 * <p>{@code app.auth.enabled=false} 时整体不注册（仅测试切片使用；
 * 生产保持默认 {@code true}）。本类实现 {@code WebMvcConfigurer}，会被
 * {@code @WebMvcTest} 切片加载，因此属性门控必须在此处判断而非依赖自动配置。</p>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

	private final AppProperties properties;

	/**
	 * @param properties {@code app.*} 配置（{@code app.auth.enabled} 门控）
	 */
	public SaTokenConfig(AppProperties properties) {
		this.properties = properties;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		if (!this.properties.auth().enabled()) {
			return;
		}
		registry.addInterceptor(new SaInterceptor((handler) -> StpUtil.checkLogin()))
			.addPathPatterns("/api/**")
			.excludePathPatterns("/api/auth/login");
	}
}
