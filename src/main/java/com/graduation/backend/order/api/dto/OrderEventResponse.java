package com.graduation.backend.order.api.dto;

import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.order.domain.OrderAction;
import com.graduation.backend.order.domain.OrderEvent;
import com.graduation.backend.order.domain.OrderStatus;

import java.time.Instant;

/**
 * 订单时间线条目。
 *
 * <p>{@code operator} 由服务层解析成用户摘要：事件表只存 {@code operator_id}，
 * 响应要昵称与头像，且不能带上 openid 之类的敏感字段。
 */
public record OrderEventResponse(
        Long id,
        OrderAction action,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        UserSummaryResponse operator,
        String remark,
        Instant createdAt) {

    public static OrderEventResponse from(OrderEvent event, UserSummaryResponse operator) {
        return new OrderEventResponse(
                event.getId(), event.getAction(), event.getFromStatus(), event.getToStatus(),
                operator, event.getRemark(), event.getCreatedAt());
    }
}
