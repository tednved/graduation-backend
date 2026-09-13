package com.graduation.backend.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 下单时固化的商品与双方昵称快照，每个订单只追加一条，不提供更新方法。
 *
 * <p>主键就是订单 ID（一对一），因此不需要自增列。商品后续改标题/改价/换图都不影响
 * 已生成订单的展示——历史订单必须能还原成交时的样子。
 */
@Entity
@Table(name = "order_snapshots")
public class OrderSnapshot {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "item_title", nullable = false, length = 80)
    private String itemTitle;

    @Column(name = "item_image_url", length = 500)
    private String itemImageUrl;

    @Column(name = "item_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal itemPrice;

    @Column(name = "seller_nickname", nullable = false, length = 30)
    private String sellerNickname;

    @Column(name = "buyer_nickname", nullable = false, length = 30)
    private String buyerNickname;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderSnapshot() {
    }

    public static OrderSnapshot of(Long orderId, String itemTitle, String itemImageUrl, BigDecimal itemPrice,
                                   String sellerNickname, String buyerNickname, Instant now) {
        OrderSnapshot snapshot = new OrderSnapshot();
        snapshot.orderId = orderId;
        snapshot.itemTitle = itemTitle;
        snapshot.itemImageUrl = itemImageUrl;
        snapshot.itemPrice = itemPrice;
        snapshot.sellerNickname = sellerNickname;
        snapshot.buyerNickname = buyerNickname;
        snapshot.createdAt = now;
        return snapshot;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public String getItemImageUrl() {
        return itemImageUrl;
    }

    public BigDecimal getItemPrice() {
        return itemPrice;
    }

    public String getSellerNickname() {
        return sellerNickname;
    }

    public String getBuyerNickname() {
        return buyerNickname;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
