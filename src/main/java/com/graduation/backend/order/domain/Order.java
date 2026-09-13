package com.graduation.backend.order.domain;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单聚合根。
 *
 * <p>状态机集中在这里：{@code confirm}/{@code reject}/{@code cancel}/{@code deliver}/{@code receive}
 * 各自校验「操作人身份 + 当前状态」，控制器与查询服务不得直接改字段。
 *
 * <p>授权失败与状态冲突用不同错误码：不是本单参与方一律 403 {@code ORDER_OPERATION_FORBIDDEN}
 * （契约把不可见与无权统一成这一个码），状态不允许该动作则是 409
 * {@code ORDER_ILLEGAL_STATUS_TRANSITION}。
 *
 * <p>{@code @Version} 提供乐观锁；写路径仍会在事务内悲观锁定订单行，使并发操作串行化，
 * 不让「状态已变」表现成提交时的 500。
 *
 * <p>金额取下单时的商品快照，商品后续改价不影响已生成的订单。
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "client_request_id", nullable = false, length = 64)
    private String clientRequestId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_mode", nullable = false, length = 32)
    private TradeMode tradeMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Order() {
    }

    /** 新建订单，固定从 {@code PENDING_CONFIRMATION} 开始。订单号与金额由服务层决定。 */
    public static Order create(String orderNo, Long itemId, Long buyerId, Long sellerId,
                               String clientRequestId, BigDecimal amount, TradeMode tradeMode, Instant now) {
        Order order = new Order();
        order.orderNo = orderNo;
        order.itemId = itemId;
        order.buyerId = buyerId;
        order.sellerId = sellerId;
        order.clientRequestId = clientRequestId;
        order.amount = amount;
        order.tradeMode = tradeMode;
        order.status = OrderStatus.PENDING_CONFIRMATION;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    /** 卖家接单。 */
    public void confirm(Long actorId, Instant now) {
        requireSeller(actorId);
        requireStatus(OrderStatus.PENDING_CONFIRMATION, "当前状态不允许接单");
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = now;
        this.updatedAt = now;
    }

    /** 卖家拒单；原因必填由请求 DTO 保证。 */
    public void reject(Long actorId, String reason, Instant now) {
        requireSeller(actorId);
        requireStatus(OrderStatus.PENDING_CONFIRMATION, "当前状态不允许拒单");
        this.status = OrderStatus.REJECTED;
        this.cancelReason = reason;
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    /**
     * 取消订单。
     *
     * <p>{@code PENDING_CONFIRMATION} 只有买家能取消（卖家走拒单）；{@code CONFIRMED} 双方都可以；
     * 其余状态（含 {@code PENDING_RECEIPT}）不允许普通用户取消，返回状态转换冲突。
     */
    public void cancel(Long actorId, String reason, Instant now) {
        requireParticipant(actorId);
        if (status == OrderStatus.PENDING_CONFIRMATION) {
            if (!isBuyer(actorId)) {
                throw new BusinessException(ErrorCode.ORDER_OPERATION_FORBIDDEN, "待确认的订单只能由买家取消");
            }
        } else if (status != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ORDER_ILLEGAL_STATUS_TRANSITION, "当前状态不允许取消");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    /** 卖家确认交付。 */
    public void deliver(Long actorId, Instant now) {
        requireSeller(actorId);
        requireStatus(OrderStatus.CONFIRMED, "当前状态不允许交付");
        this.status = OrderStatus.PENDING_RECEIPT;
        this.deliveredAt = now;
        this.updatedAt = now;
    }

    /** 买家确认收货。商品转 {@code SOLD} 由服务层在同事务内完成。 */
    public void receive(Long actorId, Instant now) {
        requireBuyer(actorId);
        requireStatus(OrderStatus.PENDING_RECEIPT, "当前状态不允许确认收货");
        this.status = OrderStatus.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
    }

    /**
     * 当前用户在现状态下可执行的订单动作。
     *
     * <p>纯计算：只看状态与身份。契约要求前端按它展示按钮，但后端每次请求仍重新校验，
     * 因此这里的结果不构成授权。
     */
    public List<OrderAction> allowedActionsFor(Long viewerId) {
        if (viewerId == null || !isParticipant(viewerId)) {
            return List.of();
        }
        List<OrderAction> actions = new ArrayList<>(2);
        switch (status) {
            case PENDING_CONFIRMATION -> {
                if (isSeller(viewerId)) {
                    actions.add(OrderAction.CONFIRM);
                    actions.add(OrderAction.REJECT);
                } else {
                    actions.add(OrderAction.CANCEL);
                }
            }
            case CONFIRMED -> {
                actions.add(OrderAction.CANCEL);
                if (isSeller(viewerId)) {
                    actions.add(OrderAction.DELIVER);
                }
            }
            case PENDING_RECEIPT -> {
                if (isBuyer(viewerId)) {
                    actions.add(OrderAction.RECEIVE);
                }
            }
            default -> {
                // COMPLETED / CANCELLED / REJECTED 都是终态，没有任何可执行动作。
            }
        }
        return List.copyOf(actions);
    }

    public boolean isBuyer(Long userId) {
        return buyerId != null && buyerId.equals(userId);
    }

    public boolean isSeller(Long userId) {
        return sellerId != null && sellerId.equals(userId);
    }

    public boolean isParticipant(Long userId) {
        return isBuyer(userId) || isSeller(userId);
    }

    /** 订单已完成，才允许双方评价。 */
    public boolean isCompleted() {
        return status == OrderStatus.COMPLETED;
    }

    private void requireParticipant(Long actorId) {
        if (!isParticipant(actorId)) {
            throw new BusinessException(ErrorCode.ORDER_OPERATION_FORBIDDEN, "不是该订单的参与方");
        }
    }

    private void requireBuyer(Long actorId) {
        requireParticipant(actorId);
        if (!isBuyer(actorId)) {
            throw new BusinessException(ErrorCode.ORDER_OPERATION_FORBIDDEN, "该操作只能由买家执行");
        }
    }

    /**
     * 校验操作人是本单卖家。
     *
     * <p>公开给应用层：接单要同时检查商品状态，必须先判身份再碰商品，否则非参与方能靠
     * 商品状态的错误码反推订单进度（403 与 409 的区别本身就是信息）。身份规则只有这一份实现，
     * 应用层不得自行判断买卖双方。
     */
    public void requireSeller(Long actorId) {
        requireParticipant(actorId);
        if (!isSeller(actorId)) {
            throw new BusinessException(ErrorCode.ORDER_OPERATION_FORBIDDEN, "该操作只能由卖家执行");
        }
    }

    private void requireStatus(OrderStatus expected, String message) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.ORDER_ILLEGAL_STATUS_TRANSITION, message);
        }
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getBuyerId() {
        return buyerId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TradeMode getTradeMode() {
        return tradeMode;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
