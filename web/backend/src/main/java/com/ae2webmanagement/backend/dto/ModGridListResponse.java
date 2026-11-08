package com.ae2webmanagement.backend.dto;

import java.util.List;

/**
 * 模组 {@code GET /grids} 响应的反序列化目标（模组契约形状）。
 *
 * <p>与 web 侧 {@link GridListResponse} 分离：模组响应不含 {@code unlocked}
 * （后端计算），且旧版模组（&lt;0.0.6）不含 {@code hasPassword} —— 反序列化为
 * {@code null} 由 {@code GridService} 按「无密码 = 公开」归一。
 * Jackson 3 默认对缺失的 primitive record 组件报错，故模组侧用 {@code Boolean}。</p>
 *
 * @param grids 模组网格摘要列表
 */
public record ModGridListResponse(List<ModGridSummaryResponse> grids) {
}
