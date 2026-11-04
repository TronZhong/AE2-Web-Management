package com.ae2webmanagement.backend.dto;

import java.util.List;

/**
 * 模组 {@code GET /networks} 响应的反序列化目标（模组契约形状）。
 *
 * <p>与 web 侧 {@link NetworkListResponse} 分离：模组响应不含 {@code unlocked}
 * （后端计算），且旧版模组（&lt;0.0.6）不含 {@code hasPassword} —— 反序列化为
 * {@code null} 由 {@code NetworkService} 按「无密码 = 公开」归一。
 * Jackson 3 默认对缺失的 primitive record 组件报错，故模组侧用 {@code Boolean}。</p>
 *
 * @param networks 模组网络摘要列表
 */
public record ModNetworkListResponse(List<ModNetworkSummaryResponse> networks) {
}
