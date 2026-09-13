package com.graduation.backend.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 订单双向评价，只追加不修改。
 *
 * <p>{@code (order_id, reviewer_id)} 上有唯一键：同一订单同一评价人只能有一条。
 * 服务层先查一次给出 409，唯一键是并发下的最终防线。
 *
 * <p>{@code reviewee_id} 由服务端按对端推导，不接受客户端传入——否则可以给任意人刷分。
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId;

    // 库列是 TINYINT UNSIGNED（1..5），领域里仍用 Integer；
    // 不显式声明 JDBC 类型时 Hibernate 会期望 INTEGER，ddl-auto=validate 直接启动失败。
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "content", length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReviewStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Review() {
    }

    public static Review of(Long orderId, Long reviewerId, Long revieweeId, Integer rating,
                            String content, Instant now) {
        Review review = new Review();
        review.orderId = orderId;
        review.reviewerId = reviewerId;
        review.revieweeId = revieweeId;
        review.rating = rating;
        review.content = content;
        review.status = ReviewStatus.VISIBLE;
        review.createdAt = now;
        review.updatedAt = now;
        return review;
    }

    public boolean isVisible() {
        return status == ReviewStatus.VISIBLE;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public Long getRevieweeId() {
        return revieweeId;
    }

    public Integer getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
