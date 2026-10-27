package com.ae2webmanagement.backend.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 后端与模组本地 API 之间的唯一通信入口。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>所有对模组的调用都经由本类，service / controller 中禁止直接使用 {@link RestClient}。</li>
 *   <li>模组端点只绑定回环地址，通过 {@code X-Shared-Secret} 头做机器级认证。</li>
 *   <li>请求/响应对象定义在 {@code dto} 包中，尚未定义字段。</li>
 * </ul>
 */
@Component
public class ModApiClient {

	private final RestClient modRestClient;

	public ModApiClient(RestClient modRestClient) {
		this.modRestClient = modRestClient;
	}

	// TODO 定义库存快照获取、合成命令提交等方法，返回 dto 包中的契约类型。
}