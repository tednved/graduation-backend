package com.graduation.backend.item.api.dto;

import com.graduation.backend.item.domain.ItemStatus;
import com.graduation.backend.item.query.ItemCardRow;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 契约 {@code MyItemSummary}：「我的发布」列表项。
 *
 * <p>比 {@code ItemCard} 多出 {@code adminLock}/{@code offShelfReason}/{@code version}，
 * 让卖家能直接看到被强制下架的原因与可提交的版本号。
 */
public record MyItemSummaryResponse(
        Long id,
        String title,
        BigDecimal price,
        ItemStatus status,
        String coverImageUrl,
        boolean adminLock,
        String offShelfReason,
        Integer favoriteCount,
        Integer viewCount,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        Integer version) {

    public static MyItemSummaryResponse from(ItemCardRow row) {
        return new MyItemSummaryResponse(
                row.getId(),
                row.getTitle(),
                row.getPrice(),
                row.getStatus() == null ? null : ItemStatus.valueOf(row.getStatus()),
                row.getCoverImageUrl(),
                Boolean.TRUE.equals(row.getAdminLock()),
                row.getOffShelfReason(),
                row.getFavoriteCount() == null ? 0 : row.getFavoriteCount(),
                row.getViewCount() == null ? 0 : row.getViewCount(),
                row.getPublishedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getVersion() == null ? 0 : row.getVersion().intValue());
    }
}
