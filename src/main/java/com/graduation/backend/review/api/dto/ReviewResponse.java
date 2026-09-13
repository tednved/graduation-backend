package com.graduation.backend.review.api.dto;

import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.review.domain.Review;
import com.graduation.backend.review.domain.ReviewStatus;

import java.time.Instant;

/** 评价响应。评价人与被评价人都只给公开摘要，不含 openid、手机号或认证信息。 */
public record ReviewResponse(
        Long id,
        Long orderId,
        UserSummaryResponse reviewer,
        UserSummaryResponse reviewee,
        Integer rating,
        String content,
        ReviewStatus status,
        Instant createdAt) {

    public static ReviewResponse of(Review review, UserSummaryResponse reviewer, UserSummaryResponse reviewee) {
        return of(review.getId(), review.getOrderId(), reviewer, reviewee,
                review.getRating(), review.getContent(), review.getStatus(), review.getCreatedAt());
    }

    /** 列表查询直接用投影行的字段构造，不再加载实体。 */
    public static ReviewResponse of(Long id, Long orderId, UserSummaryResponse reviewer,
                                    UserSummaryResponse reviewee, Integer rating, String content,
                                    ReviewStatus status, Instant createdAt) {
        return new ReviewResponse(id, orderId, reviewer, reviewee, rating, content, status, createdAt);
    }
}
