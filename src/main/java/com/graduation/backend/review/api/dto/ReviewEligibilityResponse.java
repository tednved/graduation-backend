package com.graduation.backend.review.api.dto;

/**
 * 订单的双方评价状态。
 *
 * <p>{@code myReviewId} 与 {@code counterpartReviewId} 为空表示「还没评价」，
 * 与「已评价」区分开；{@code canReview} 是后端算好的结论，前端不自行推导。
 */
public record ReviewEligibilityResponse(
        Long orderId,
        boolean canReview,
        Long myReviewId,
        boolean counterpartReviewed,
        Long counterpartReviewId) {
}
