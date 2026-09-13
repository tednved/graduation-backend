package com.graduation.backend.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 站内业务消息。
 *
 * <p>{@code bizType}/{@code bizId} 是跨领域的逻辑标识（如 {@code ORDER}/{@code ITEM} + 主键），
 * 用于前端跳转，刻意不建多态外键——消息不该因为目标被删除而失去意义。
 *
 * <p>已读只有「未读 → 已读」一个方向，{@link #markRead} 幂等。
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    @Column(name = "biz_id", nullable = false)
    private Long bizId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public static Notification of(Long userId, NotificationType type, String title, String content,
                                 String bizType, Long bizId, Instant now) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.type = type;
        notification.title = title;
        notification.content = content;
        notification.bizType = bizType;
        notification.bizId = bizId;
        notification.createdAt = now;
        return notification;
    }

    /** 标记已读；已读再次调用不改变任何字段（幂等）。 */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean belongsTo(Long otherUserId) {
        return userId != null && userId.equals(otherUserId);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getBizType() {
        return bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
