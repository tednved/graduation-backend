package com.graduation.backend.review.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 评价写入边界（JPA）。
 *
 * <p>列表与评分分布走 MyBatis-Plus，这里只承担写路径与聚合重算。
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 同一订单同一评价人最多一条，服务层据此给出 409。 */
    Optional<Review> findByOrderIdAndReviewerId(Long orderId, Long reviewerId);

    /**
     * 重算被评价人的平均分与评价数。
     *
     * <p>聚合不在应用层做「读旧值再加一」：并行两笔评价会丢更新。交给数据库计算，且只统计
     * {@code VISIBLE} 评价。
     */
    long countByRevieweeIdAndStatus(Long revieweeId, ReviewStatus status);

    @Query("select avg(r.rating) from Review r where r.revieweeId = :revieweeId and r.status = :status")
    Double averageRating(@Param("revieweeId") Long revieweeId, @Param("status") ReviewStatus status);
}
