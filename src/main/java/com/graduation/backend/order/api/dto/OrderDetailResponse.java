package com.graduation.backend.order.api.dto;

import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.order.domain.OrderAction;
import com.graduation.backend.order.domain.OrderStatus;
import com.graduation.backend.order.domain.TradeMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 订单详情。
 *
 * <p>{@code allowedActions} 只服务于按钮展示：后端处理每个命令时仍会重新校验身份与状态，
 * 因此它不构成授权依据。
 */
public record OrderDetailResponse(
        Long id,
        String orderNo,
        OrderStatus status,
        TradeMode tradeMode,
        BigDecimal amount,
        String cancelReason,
        OrderItemSnapshotResponse item,
        UserSummaryResponse buyer,
        UserSummaryResponse seller,
        List<OrderAction> allowedActions,
        List<OrderEventResponse> events,
        Instant createdAt,
        Instant confirmedAt,
        Instant deliveredAt,
        Instant completedAt,
        Instant cancelledAt,
        Instant updatedAt) {
}
