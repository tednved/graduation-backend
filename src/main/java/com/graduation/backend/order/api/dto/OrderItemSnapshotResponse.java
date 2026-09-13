package com.graduation.backend.order.api.dto;

import com.graduation.backend.order.domain.OrderSnapshot;

import java.math.BigDecimal;

/**
 * 下单时固化的商品快照。
 *
 * <p>商品后续被改标题、改价或换图都不影响这里——订单展示必须能还原成交时的样子。
 * {@code imageUrl} 可能为 null（商品当时没有图片，或图片已进入待清理状态）。
 */
public record OrderItemSnapshotResponse(
        Long itemId,
        String title,
        String imageUrl,
        BigDecimal price) {

    public static OrderItemSnapshotResponse of(Long itemId, String title, String imageUrl, BigDecimal price) {
        return new OrderItemSnapshotResponse(itemId, title, imageUrl, price);
    }

    /** 快照表主键是订单 ID，商品 ID 需由订单本身提供。 */
    public static OrderItemSnapshotResponse from(Long itemId, OrderSnapshot snapshot) {
        return of(itemId, snapshot.getItemTitle(), snapshot.getItemImageUrl(), snapshot.getItemPrice());
    }
}
