package com.graduation.backend.notification.domain;

/** 站内消息类型（契约 §4）。 */
public enum NotificationType {

    ORDER_CREATED,
    ORDER_CONFIRMED,
    ORDER_REJECTED,
    ORDER_CANCELLED,
    ORDER_DELIVERED,
    ORDER_COMPLETED,
    REVIEW_RECEIVED,
    SYSTEM
}
