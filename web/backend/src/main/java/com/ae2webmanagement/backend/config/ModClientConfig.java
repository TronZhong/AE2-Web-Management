package com.ae2webmanagement.backend.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 组装访问模组本地 API 的 {@link RestClient}。
 *
 * <p>模组端点只绑定 127.0.0.1，并通过 {@code X-Shared-Secret} 头做机器级认证。
 * 当前项目只有一个模组端点，因此直接暴露一个 {@link RestClient} bean；
 * 若未来对接多个模组实例，再改为按 {@code @Qualifier} 区分。</p>
 *
 * <p>连接/读取超时见 {@link #modRequestFactory()}：模组未启动或游戏关闭时，
 * 对模组的调用必须在秒级失败并翻译为 {@code mod_unreachable}，不能吊死后端线程。</p>
 *
 * <p>{@code @EnableConfigurationProperties} 使本配置自给自足：即使只在测试切片
 * 上下文中加载（主类的 {@code @ConfigurationPropertiesScan} 不生效），
 * {@link AppProperties} 也能被注册并绑定 {@code app.*}。</p>
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class ModClientConfig {

	/** 连接超时：模组仅监听回环地址，握手应立即完成；连接不上视为模组不可达。 */
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

	/** 读取超时：模组接口只读并发缓存并立即返回，5s 足以覆盖本机排队场景。 */
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	/**
	 * 构建 {@link RestClient} bean：baseUrl、共享密钥默认头与带超时的请求工厂。
	 *
	 * @param properties 外部化配置（app.mod.base-url / app.mod.shared-secret）
	 * @return 访问模组本地 API 的 RestClient
	 */
	@Bean
	RestClient modRestClient(AppProperties properties) {
		return RestClient.builder()
			.baseUrl(properties.mod().baseUrl())
			.requestFactory(modRequestFactory())
			.defaultHeader("X-Shared-Secret", properties.mod().sharedSecret())
			.build();
	}

	/**
	 * 构建带超时的请求工厂：连接 2s / 读取 5s（见仓库开发计划的既定决策）。
	 *
	 * <p>Spring Boot 4.1 依赖树不含 Boot 侧的 {@code ClientHttpRequestFactorySettings}，
	 * 因此使用 Spring Framework 自带的 {@link JdkClientHttpRequestFactory} 等价实现：
	 * 连接超时走 JDK {@link HttpClient}，读取超时走工厂的 {@code setReadTimeout}。
	 * 包级可见以便 {@code ModClientConfigTests} 直接复用同一构造路径验证超时行为。
	 *
	 * @return 带连接/读取超时的请求工厂
	 */
	static JdkClientHttpRequestFactory modRequestFactory() {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return requestFactory;
	}
}
