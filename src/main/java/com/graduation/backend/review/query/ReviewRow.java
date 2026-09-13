package com.graduation.backend.review.query;

import com.graduation.backend.review.domain.ReviewStatus;

import java.time.Instant;

/**
 * 用户收到的评价行（MyBatis 直接映射）。
 *
 * <p>{@code reviewer*} 来自 {@code users}：评价列表要展示评价人昵称与头像，
 * 响应里绝不能出现 openid 等敏感字段。
 */
public class ReviewRow {

    private Long reviewId;
    private Long orderId;
    private Long reviewerId;
    private String reviewerNickname;
    private String reviewerAvatarUrl;
    private Integer rating;
    private String content;
    private ReviewStatus status;
    private Instant createdAt;

    public Long getReviewId() {
        return reviewId;
    }

    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewerNickname() {
        return reviewerNickname;
    }

    public void setReviewerNickname(String reviewerNickname) {
        this.reviewerNickname = reviewerNickname;
    }

    public String getReviewerAvatarUrl() {
        return reviewerAvatarUrl;
    }

    public void setReviewerAvatarUrl(String reviewerAvatarUrl) {
        this.reviewerAvatarUrl = reviewerAvatarUrl;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
