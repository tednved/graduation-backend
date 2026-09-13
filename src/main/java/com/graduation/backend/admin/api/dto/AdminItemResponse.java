package com.graduation.backend.admin.api.dto;

import com.graduation.backend.admin.query.AdminItemRow;
import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.item.api.dto.CampusRefResponse;
import com.graduation.backend.item.api.dto.CategoryRefResponse;
import com.graduation.backend.item.domain.ItemStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** 管理端商品条目：不受 {@code ON_SALE} 限制，含管理员锁定与下架原因。 */
public record AdminItemResponse(
        Long id,
        String title,
        BigDecimal price,
        ItemStatus status,
        UserSummaryResponse seller,
        CategoryRefResponse category,
        CampusRefResponse campus,
        boolean adminLock,
        String offShelfReason,
        Integer favoriteCount,
        Integer viewCount,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminItemResponse fromRow(AdminItemRow row) {
        return new AdminItemResponse(
                row.getId(),
                row.getTitle(),
                row.getPrice(),
                row.getStatus(),
                new UserSummaryResponse(row.getSellerId(), row.getSellerNickname(), row.getSellerAvatarUrl()),
                row.getCategoryId() == null ? null : new CategoryRefResponse(row.getCategoryId(), row.getCategoryName()),
                row.getCampusId() == null ? null : new CampusRefResponse(row.getCampusId(), row.getCampusName()),
                row.getAdminLock(),
                row.getOffShelfReason(),
                row.getFavoriteCount(),
                row.getViewCount(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
