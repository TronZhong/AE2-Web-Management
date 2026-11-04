package com.ae2webmanagement.backend.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import com.ae2webmanagement.backend.client.ModApiClient;
import com.ae2webmanagement.backend.client.ModApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link NetworkService} 错误翻译表测试（见类注释）：
 * accessId 校验、mod_unreachable、mod_unavailable、其余错误码透传。
 */
@ExtendWith(MockitoExtension.class)
class NetworkServiceTests {

	@Mock
	private ModApiClient modApiClient;

	@InjectMocks
	private NetworkService service;

	/** accessId 非法 → 400 invalid_access_id，且不触达模组客户端。 */
	@Test
	void invalidAccessIdMapsTo400AndSkipsModCall() {
		assertThatThrownBy(() -> this.service.getSnapshot("not-a-uuid"))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(400);
				assertThat(e.code()).isEqualTo("invalid_access_id");
			});
		verifyNoInteractions(this.modApiClient);
	}

	/** 连接失败 / 超时 → 503 mod_unreachable。 */
	@Test
	void connectionFailureMapsToModUnreachable() {
		when(this.modApiClient.listNetworks()).thenThrow(new ResourceAccessException("connect timed out"));

		assertThatThrownBy(this.service::listNetworks)
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(503);
				assertThat(e.code()).isEqualTo("mod_unreachable");
			});
	}

	/** 模组 401（共享密钥不匹配）→ 503 mod_unavailable。 */
	@Test
	void unauthorizedMapsToModUnavailable() {
		when(this.modApiClient.listNetworks()).thenThrow(new ModApiException(401, "unauthorized"));

		assertThatThrownBy(this.service::listNetworks)
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(503);
				assertThat(e.code()).isEqualTo("mod_unavailable");
			});
	}

	/** 模组其余错误码原样透传（状态码 + 错误码都不变）。 */
	@Test
	void otherModErrorsPassThrough() {
		when(this.modApiClient.getSnapshot(any(UUID.class))).thenThrow(new ModApiException(404, "snapshot_not_found"));

		assertThatThrownBy(() -> this.service.getSnapshot("0f2d6c1e-4b7a-4c1e-9a3f-2f8f6f41a001"))
			.isInstanceOfSatisfying(ModApiException.class, e -> {
				assertThat(e.status()).isEqualTo(404);
				assertThat(e.code()).isEqualTo("snapshot_not_found");
			});
	}
}
