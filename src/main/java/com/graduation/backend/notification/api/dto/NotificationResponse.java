package com.graduation.backend.notification.api.dto;

import com.graduation.backend.notification.domain.Notification;
import com.graduation.backend.notification.domain.NotificationType;
import com.graduation.backend.notification.query.NotificationRow;

import java.time.Instant;

/**
 * 消息响应。
 *
 * <p>{@code read} 由 {@code readAt} 派生，客户端只判断这一个布尔值即可，
 * 不必自己比较时间戳。
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String content,
        String bizType,
        Long bizId,
        boolean read,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getBizType(),
                notification.getBizId(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }

    public static NotificationResponse fromRow(NotificationRow row) {
        return new NotificationResponse(
                row.getId(),
                NotificationType.valueOf(row.getType()),
                row.getTitle(),
                row.getContent(),
                row.getBizType(),
                row.getBizId(),
                row.getReadAt() != null,
                row.getReadAt(),
                row.getCreatedAt());
    }
}
