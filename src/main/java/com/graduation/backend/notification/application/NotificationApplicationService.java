package com.graduation.backend.notification.application;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.notification.api.dto.ReadAllResponse;
import com.graduation.backend.notification.domain.Notification;
import com.graduation.backend.notification.domain.NotificationRepository;
import com.graduation.backend.user.application.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 消息写路径：标记已读。
 *
 * <p>「不存在」与「不属于本人」统一返回 404 {@code RESOURCE_NOT_FOUND}：区分两者等于告诉调用方
 * 「这条消息存在但不是你的」，会泄露他人消息的存在性。
 */
@Service
public class NotificationApplicationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final Clock clock;

    public NotificationApplicationService(NotificationRepository notificationRepository,
                                          UserService userService,
                                          Clock clock) {
        this.notificationRepository = notificationRepository;
        this.userService = userService;
        this.clock = clock;
    }

    /** 标记单条已读；已读再调用仍是成功（契约要求幂等）。 */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        userService.requireActiveUser(userId);
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "消息不存在"));
        notification.markRead(clock.instant());
    }

    /** 全部标记已读，返回本次真正变更的条数。 */
    @Transactional
    public ReadAllResponse markAllRead(Long userId) {
        userService.requireActiveUser(userId);
        int updated = notificationRepository.markAllRead(userId, clock.instant());
        return new ReadAllResponse(updated);
    }
}
