package com.graduation.backend.notification.api.dto;

/** 未读消息数响应。计数用 {@code Integer}，因此不会被 ID 字符串化规则命中。 */
public record UnreadCountResponse(Integer count) {
}
