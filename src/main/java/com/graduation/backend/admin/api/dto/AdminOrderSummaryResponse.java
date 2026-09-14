package com.graduation.backend.admin.api.dto;

import com.graduation.backend.admin.query.AdminOrderRow;
import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.order.api.dto.OrderItemSnapshotResponse;
import com.graduation.backend.order.domain.OrderStatus;
import com.graduation.backend.order.domain.TradeMode;

import java.math.BigDecimal;
import java.time.Instant;

/** 管理端订单条目：同时展示买卖双方，不复用带「对端」语义的个人订单 DTO。 */
public record AdminOrderSummaryResponse(
        Long id,
        String orderNo,
        OrderStatus status,
        TradeMode tradeMode,
        BigDecimal amount,
        OrderItemSnapshotResponse item,
        UserSummaryResponse buyer,
        UserSummaryResponse seller,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminOrderSummaryResponse fromRow(AdminOrderRow row) {
        return new AdminOrderSummaryResponse(
                row.getOrderId(), row.getOrderNo(), row.getStatus(), row.getTradeMode(), row.getAmount(),
                OrderItemSnapshotResponse.of(row.getItemId(), row.getItemTitle(), row.getItemImageUrl(),
                        row.getItemPrice()),
                new UserSummaryResponse(row.getBuyerId(), row.getBuyerNickname(), row.getBuyerAvatarUrl()),
                new UserSummaryResponse(row.getSellerId(), row.getSellerNickname(), row.getSellerAvatarUrl()),
                row.getCreatedAt(), row.getUpdatedAt());
    }
}
