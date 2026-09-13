package com.graduation.backend.order.application;

import com.graduation.backend.order.api.dto.OrderDetailResponse;

/**
 * 创建订单的结果。
 *
 * <p>{@code created} 决定 HTTP 状态码：首次创建 201，幂等重放同一 {@code clientRequestId} 返回 200，
 * 但两者响应体都是原订单的详情（重放时以已存在的订单为准，不重新计算）。
 */
public record OrderCreationResult(OrderDetailResponse detail, boolean created) {

    public static OrderCreationResult created(OrderDetailResponse detail) {
        return new OrderCreationResult(detail, true);
    }

    public static OrderCreationResult replayed(OrderDetailResponse detail) {
        return new OrderCreationResult(detail, false);
    }
}
