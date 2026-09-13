package com.graduation.backend.notification.api.dto;

/** 全部标记已读的结果：本次由未读变为已读的条数（已读的旧消息不计入）。 */
public record ReadAllResponse(Integer updatedCount) {
}
