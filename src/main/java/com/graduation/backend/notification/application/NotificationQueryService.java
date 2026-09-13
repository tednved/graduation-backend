package com.graduation.backend.notification.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.notification.api.dto.NotificationResponse;
import com.graduation.backend.notification.api.dto.UnreadCountResponse;
import com.graduation.backend.notification.domain.NotificationRepository;
import com.graduation.backend.notification.domain.NotificationType;
import com.graduation.backend.notification.query.NotificationQueryMapper;
import com.graduation.backend.notification.query.NotificationRow;
import com.graduation.backend.user.application.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息读取：列表与未读数。
 *
 * <p>查询一律带当前用户 ID，且过滤条件写在 SQL 里——“别人的消息”从查询层面就取不到。
 */
@Service
public class NotificationQueryService {

    private final NotificationQueryMapper notificationQueryMapper;
    private final NotificationRepository notificationRepository;
    private final UserService userService;

    public NotificationQueryService(NotificationQueryMapper notificationQueryMapper,
                                    NotificationRepository notificationRepository,
                                    UserService userService) {
        this.notificationQueryMapper = notificationQueryMapper;
        this.notificationRepository = notificationRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationResponse> list(Long userId, NotificationType type, Boolean read, int page, int size) {
        // 事务内重新读库：被禁用账号带旧 Token 访问时必须按 403 USER_DISABLED 处理。
        userService.requireActiveUser(userId);
        IPage<NotificationRow> result = notificationQueryMapper.selectNotifications(
                new Page<>(page + 1L, size),
                userId,
                type == null ? null : type.name(),
                read);
        return PageResult.of(page, size, result.getTotal(),
                result.getRecords().stream().map(NotificationResponse::fromRow).toList());
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        userService.requireActiveUser(userId);
        return new UnreadCountResponse(Math.toIntExact(notificationRepository.countByUserIdAndReadAtIsNull(userId)));
    }
}
