package com.ae2webmanagement.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 组装访问模组本地 API 的 {@link RestClient}。
 *
 * <p>模组端点只绑定 127.0.0.1，并通过 {@code X-Shared-Secret} 头做机器级认证。
 * 当前项目只有一个模组端点，因此直接暴露一个 {@link RestClient} bean；
 * 若未来对接多个模组实例，再改为按 {@code @Qualifier} 区分。
 */
@Configuration
public class ModClientConfig {

	@Bean
	RestClient modRestClient(AppProperties properties) {
		return RestClient.builder()
			.baseUrl(properties.mod().baseUrl())
			.defaultHeader("X-Shared-Secret", properties.mod().sharedSecret())
			.build();
	}
}