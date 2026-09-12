package com.graduation.backend.item.api.dto;

import com.graduation.backend.item.domain.ItemCondition;
import com.graduation.backend.item.domain.ItemStatus;
import com.graduation.backend.item.query.ItemCardRow;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 契约 {@code ItemCard}：搜索列表与收藏列表共用的卡片，只带首图。
 *
 * <p>由 MyBatis 行投影组装；两个列表用同一个类型，避免字段口径分叉。
 * {@code condition} 来自库列 {@code condition_level}。
 */
public record ItemCardResponse(
        Long id,
        String title,
        BigDecimal price,
        BigDecimal originalPrice,
        ItemCondition condition,
        ItemStatus status,
        String coverImageUrl,
        CampusRefResponse campus,
        CategoryRefResponse category,
        Integer favoriteCount,
        Integer viewCount,
        Instant publishedAt) {

    public static ItemCardResponse from(ItemCardRow row) {
        return new ItemCardResponse(
                row.getId(),
                row.getTitle(),
                row.getPrice(),
                row.getOriginalPrice(),
                row.getConditionLevel() == null ? null : ItemCondition.valueOf(row.getConditionLevel()),
                row.getStatus() == null ? null : ItemStatus.valueOf(row.getStatus()),
                row.getCoverImageUrl(),
                row.getCampusId() == null ? null : new CampusRefResponse(row.getCampusId(), row.getCampusName()),
                row.getCategoryId() == null ? null : new CategoryRefResponse(row.getCategoryId(), row.getCategoryName()),
                row.getFavoriteCount() == null ? 0 : row.getFavoriteCount(),
                row.getViewCount() == null ? 0 : row.getViewCount(),
                row.getPublishedAt());
    }
}
