package com.graduation.backend.notification.api;

import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.notification.api.dto.NotificationResponse;
import com.graduation.backend.notification.api.dto.ReadAllResponse;
import com.graduation.backend.notification.api.dto.UnreadCountResponse;
import com.graduation.backend.notification.application.NotificationApplicationService;
import com.graduation.backend.notification.application.NotificationQueryService;
import com.graduation.backend.notification.domain.NotificationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内消息接口。
 *
 * <p>全部接口都要求登录：消息天然属于单个用户，没有匿名场景。
 * 控制器只做参数转接，用户维度过滤在服务层与 SQL 里完成。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Validated
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationApplicationService notificationApplicationService;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationQueryService notificationQueryService,
                                 NotificationApplicationService notificationApplicationService,
                                 CurrentUserService currentUserService) {
        this.notificationQueryService = notificationQueryService;
        this.notificationApplicationService = notificationApplicationService;
        this.currentUserService = currentUserService;
    }

    /** 我的消息：可按类型与已读状态筛选。 */
    @GetMapping
    public ApiResponse<PageResult<NotificationResponse>> list(
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) Boolean read,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(notificationQueryService.list(currentUserId(), type, read, page, size));
    }

    /** 未读消息数，用于消息 Tab 红点。 */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(notificationQueryService.unreadCount(currentUserId()));
    }

    /** 标记单条已读，幂等，无响应体。 */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable("id") Long id) {
        notificationApplicationService.markRead(currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /** 全部标记已读。 */
    @PutMapping("/read-all")
    public ApiResponse<ReadAllResponse> markAllRead() {
        return ApiResponse.ok(notificationApplicationService.markAllRead(currentUserId()));
    }

    private Long currentUserId() {
        return currentUserService.requireCurrentUserId();
    }
}
