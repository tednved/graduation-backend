package com.graduation.backend.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 订单状态事件，只追加、不修改。
 *
 * <p>它是订单时间线的唯一来源：详情里的 {@code events} 直接按创建时间排序返回，
 * 因此每次状态变更都必须写一条，并记录操作人与当时的 {@code requestId}。
 * 创建事件没有前置状态（{@code fromStatus} 为 {@code null}）。
 */
@Entity
@Table(name = "order_events")
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private OrderAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private OrderStatus toStatus;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "remark", length = 200)
    private String remark;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderEvent() {
    }

    public static OrderEvent of(Long orderId, OrderAction action, OrderStatus fromStatus, OrderStatus toStatus,
                               Long operatorId, String remark, String requestId, Instant now) {
        OrderEvent event = new OrderEvent();
        event.orderId = orderId;
        event.action = action;
        event.fromStatus = fromStatus;
        event.toStatus = toStatus;
        event.operatorId = operatorId;
        event.remark = remark;
        event.requestId = requestId;
        event.createdAt = now;
        return event;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderAction getAction() {
        return action;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getRemark() {
        return remark;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
