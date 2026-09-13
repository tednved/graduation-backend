package com.graduation.backend.admin.api;

import com.graduation.backend.admin.api.dto.AuditLogResponse;
import com.graduation.backend.admin.application.AuditLogQueryService;
import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.common.web.PageResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 管理端审计查询，只读。
 *
 * <p>没有写入与删除接口：审计记录只由业务操作在事务内追加，接口层不可能篡改。
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Validated
public class AdminAuditLogController {

    private final AuditLogQueryService auditLogQueryService;
    private final CurrentUserService currentUserService;

    public AdminAuditLogController(AuditLogQueryService auditLogQueryService,
                                   CurrentUserService currentUserService) {
        this.auditLogQueryService = auditLogQueryService;
        this.currentUserService = currentUserService;
    }

    /** 审计分页：按操作者、动作、目标与时间范围筛选，时间范围两端都含。 */
    @GetMapping
    public ApiResponse<PageResult<AuditLogResponse>> list(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) @Size(min = 1, max = 64) String action,
            @RequestParam(required = false) @Size(min = 1, max = 32) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(auditLogQueryService.list(
                currentUserService.requireCurrentUserId(), operatorId, action, targetType,
                targetId, from, to, page, size));
    }
}
