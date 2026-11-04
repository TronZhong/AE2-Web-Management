package com.ae2webmanagement.backend.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * {@link ModClientConfig} 超时行为测试：模拟「接受连接但永不响应」的模组端点，
 * 验证读取超时（5s）真实生效——请求以异常收尾而不是吊死后端线程。
 */
class ModClientConfigTests {

	/**
	 * 读取超时 5s：挂起的模组端点必须在 15s 内以 {@link ResourceAccessException} 失败，
	 * 且实际耗时约 5s（证明是超时触发而非立即失败）。
	 */
	@Test
	void readTimeoutFiresWhenModNeverResponds() throws IOException {
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
			Thread acceptor = new Thread(() -> {
				try {
					socket.accept();
					// 故意不写任何响应字节，模拟模组挂起
				}
				catch (IOException ignored) {
					// 测试结束时 socket 关闭，accept 抛异常属预期
				}
			});
			acceptor.setDaemon(true);
			acceptor.start();

			// 与生产 bean 走同一构造路径（ModClientConfig.modRequestFactory）
			RestClient restClient = RestClient.builder()
				.baseUrl("http://127.0.0.1:" + socket.getLocalPort())
				.requestFactory(ModClientConfig.modRequestFactory())
				.build();

			long start = System.nanoTime();
			assertTimeoutPreemptively(Duration.ofSeconds(15), () -> assertThatThrownBy(
					() -> restClient.get().uri("/networks").retrieve().body(String.class))
				.isInstanceOf(ResourceAccessException.class));
			long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
			assertThat(elapsedMillis).as("读取超时应约为 5s，实际 %dms", elapsedMillis).isGreaterThanOrEqualTo(4500);
		}
	}
}
