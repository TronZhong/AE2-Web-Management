package com.ae2webmanagement.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 后端启动类：分层骨架 + 账号认证 + 网格访问控制 + 实时推送（SSE）。
 * {@code @EnableScheduling} 开启 {@code GridStateSyncService} 的指纹轮询
 * 与 {@code RealtimeEventHub} 的心跳（roadmap 已完成项：SSE 实时推送）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}