package com.graduation.backend.item.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.item.domain.ItemAction;
import com.graduation.backend.item.domain.ItemCondition;
import com.graduation.backend.item.domain.ItemStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 契约 {@code ItemDetail}。
 *
 * <p>{@code favorited} 是 {@link Boolean} 而不是 {@code boolean}：匿名请求必须返回 {@code null}，
 * 与「未收藏」区分开。{@code isOwner}/{@code canBuy}/{@code allowedActions} 与当前登录者相关，
 * 由服务层按查看者身份计算。
 *
 * <p>{@code isOwner} 显式标注 {@link JsonProperty}：boolean 的记录分量若交给 Jackson 推导，
 * 有被当成 is-getter 而输出成 {@code owner} 的风险，契约字段名不能靠推导。
 *
 * <p>{@code version} 与计数字段用 {@code Integer}：全局 Jackson 规则会把 {@code Long} 输出成字符串，
 * 而契约要求它们是 JSON number。
 */
public record ItemDetailResponse(
        Long id,
        String title,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        ItemCondition condition,
        ItemStatus status,
        List<ItemImageResponse> images,
        UserSummaryResponse seller,
        CategoryRefResponse category,
        CampusRefResponse campus,
        Boolean favorited,
        @JsonProperty("isOwner") boolean isOwner,
        boolean canBuy,
        List<ItemAction> allowedActions,
        boolean adminLock,
        String offShelfReason,
        Integer favoriteCount,
        Integer viewCount,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        Integer version) {
}
