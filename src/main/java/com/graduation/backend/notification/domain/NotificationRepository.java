package com.graduation.backend.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** 消息写入边界与计数（JPA）。列表分页走 MyBatis-Plus。 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 未读数：只数当前用户的未读行，走 {@code (user_id, read_at, created_at)} 索引。 */
    long countByUserIdAndReadAtIsNull(Long userId);

    /**
     * 全部标记已读，返回本次真正变更的条数。
     *
     * <p>用一条 {@code UPDATE ... WHERE read_at IS NULL} 而不是「先查再逐条改」：
     * 并发下不会重复计数，返回值也正好是契约要的 {@code updatedCount}。
     */
    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
