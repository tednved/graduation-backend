package com.graduation.backend.order.api.dto;

import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.order.domain.OrderStatus;
import com.graduation.backend.order.domain.TradeMode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单列表条目。
 *
 * <p>{@code counterpart} 随视角变化：买入列表是卖家，卖出列表是买家，
 * 由 {@code side} 参数决定的查询侧选定，不在这里推断。
 */
public record OrderSummaryResponse(
        Long id,
        String orderNo,
        OrderStatus status,
        TradeMode tradeMode,
        BigDecimal amount,
        OrderItemSnapshotResponse item,
        UserSummaryResponse counterpart,
        Instant createdAt,
        Instant updatedAt) {
}
