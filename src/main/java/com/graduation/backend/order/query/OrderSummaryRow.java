package com.graduation.backend.order.query;

import com.graduation.backend.order.domain.OrderStatus;
import com.graduation.backend.order.domain.TradeMode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单列表行（MyBatis 直接映射）。
 *
 * <p>商品信息取自 {@code order_snapshots} 而不是 {@code items}：列表也要显示成交时的标题与图，
 * 商品事后被改名换图不能污染历史订单。
 *
 * <p>{@code counterpart*} 由 SQL 按买卖视角选定对端（买入取卖家、卖出取买家）。
 */
public class OrderSummaryRow {

    private Long orderId;
    private String orderNo;
    private OrderStatus status;
    private TradeMode tradeMode;
    private BigDecimal amount;
    private Long itemId;
    private String itemTitle;
    private String itemImageUrl;
    private BigDecimal itemPrice;
    private Long counterpartId;
    private String counterpartNickname;
    private String counterpartAvatarUrl;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public TradeMode getTradeMode() {
        return tradeMode;
    }

    public void setTradeMode(TradeMode tradeMode) {
        this.tradeMode = tradeMode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public void setItemTitle(String itemTitle) {
        this.itemTitle = itemTitle;
    }

    public String getItemImageUrl() {
        return itemImageUrl;
    }

    public void setItemImageUrl(String itemImageUrl) {
        this.itemImageUrl = itemImageUrl;
    }

    public BigDecimal getItemPrice() {
        return itemPrice;
    }

    public void setItemPrice(BigDecimal itemPrice) {
        this.itemPrice = itemPrice;
    }

    public Long getCounterpartId() {
        return counterpartId;
    }

    public void setCounterpartId(Long counterpartId) {
        this.counterpartId = counterpartId;
    }

    public String getCounterpartNickname() {
        return counterpartNickname;
    }

    public void setCounterpartNickname(String counterpartNickname) {
        this.counterpartNickname = counterpartNickname;
    }

    public String getCounterpartAvatarUrl() {
        return counterpartAvatarUrl;
    }

    public void setCounterpartAvatarUrl(String counterpartAvatarUrl) {
        this.counterpartAvatarUrl = counterpartAvatarUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
