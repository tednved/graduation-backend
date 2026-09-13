package com.graduation.backend.admin.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.backend.admin.api.dto.AuditLogResponse;
import com.graduation.backend.admin.query.AuditLogQueryMapper;
import com.graduation.backend.admin.query.AuditLogRow;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.user.application.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * 审计日志查询，只读。
 *
 * <p>接口不提供任何写入或删除能力：审计记录只能由业务操作在事务内追加。
 */
@Service
public class AuditLogQueryService {

    private final AuditLogQueryMapper auditLogQueryMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public AuditLogQueryService(AuditLogQueryMapper auditLogQueryMapper,
                                UserService userService,
                                ObjectMapper objectMapper) {
        this.auditLogQueryMapper = auditLogQueryMapper;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /** 审计分页：按操作者、动作、目标与时间范围筛选。 */
    @Transactional(readOnly = true)
    public PageResult<AuditLogResponse> list(Long adminId, Long operatorId, String action, String targetType,
                                             Long targetId, Instant from, Instant to, int page, int size) {
        userService.requireAdmin(adminId);
        IPage<AuditLogRow> result = auditLogQueryMapper.selectAuditLogs(
                new Page<>(page + 1L, size), operatorId, blankToNull(action), blankToNull(targetType),
                targetId, from, to);
        return PageResult.of(page, size, result.getTotal(),
                result.getRecords().stream()
                        .map(row -> AuditLogResponse.fromRow(row, objectMapper))
                        .toList());
    }

    /** 空白字符串按「不筛选」处理，避免把空串当成精确匹配条件。 */
    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
