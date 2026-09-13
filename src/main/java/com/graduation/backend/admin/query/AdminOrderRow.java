package com.graduation.backend.admin.query;

import com.graduation.backend.order.domain.OrderStatus;
import com.graduation.backend.order.domain.TradeMode;

import java.math.BigDecimal;
import java.time.Instant;

/** 管理端订单分页投影，同时包含买卖双方与下单快照。 */
public class AdminOrderRow {
    private Long orderId;
    private String orderNo;
    private OrderStatus status;
    private TradeMode tradeMode;
    private BigDecimal amount;
    private Long itemId;
    private String itemTitle;
    private String itemImageUrl;
    private BigDecimal itemPrice;
    private Long buyerId;
    private String buyerNickname;
    private String buyerAvatarUrl;
    private Long sellerId;
    private String sellerNickname;
    private String sellerAvatarUrl;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public TradeMode getTradeMode() { return tradeMode; }
    public void setTradeMode(TradeMode tradeMode) { this.tradeMode = tradeMode; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getItemTitle() { return itemTitle; }
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }
    public String getItemImageUrl() { return itemImageUrl; }
    public void setItemImageUrl(String itemImageUrl) { this.itemImageUrl = itemImageUrl; }
    public BigDecimal getItemPrice() { return itemPrice; }
    public void setItemPrice(BigDecimal itemPrice) { this.itemPrice = itemPrice; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public String getBuyerNickname() { return buyerNickname; }
    public void setBuyerNickname(String buyerNickname) { this.buyerNickname = buyerNickname; }
    public String getBuyerAvatarUrl() { return buyerAvatarUrl; }
    public void setBuyerAvatarUrl(String buyerAvatarUrl) { this.buyerAvatarUrl = buyerAvatarUrl; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getSellerNickname() { return sellerNickname; }
    public void setSellerNickname(String sellerNickname) { this.sellerNickname = sellerNickname; }
    public String getSellerAvatarUrl() { return sellerAvatarUrl; }
    public void setSellerAvatarUrl(String sellerAvatarUrl) { this.sellerAvatarUrl = sellerAvatarUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
