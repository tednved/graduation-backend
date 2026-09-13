package com.graduation.backend.admin.api.dto;

import com.graduation.backend.admin.query.AuditLogRow;
import com.graduation.backend.common.web.dto.UserSummaryResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

/**
 * 审计日志条目，只读。
 *
 * <p>{@code detail} 由写入时的脱敏 JSON 解析而来，结构随动作变化；解析失败按「无补充信息」处理，
 * 不让一条历史记录读不出来拖垮整个列表。
 */
public record AuditLogResponse(
        Long id,
        UserSummaryResponse operator,
        String action,
        String targetType,
        Long targetId,
        String requestId,
        Map<String, Object> detail,
        Instant createdAt) {

    public static AuditLogResponse fromRow(AuditLogRow row, ObjectMapper objectMapper) {
        return new AuditLogResponse(
                row.getId(),
                new UserSummaryResponse(row.getOperatorId(), row.getOperatorNickname(),
                        row.getOperatorAvatarUrl()),
                row.getAction(),
                row.getTargetType(),
                row.getTargetId(),
                row.getRequestId(),
                parseDetail(row.getDetailJson(), objectMapper),
                row.getCreatedAt());
    }

    private static Map<String, Object> parseDetail(String detailJson, ObjectMapper objectMapper) {
        if (detailJson == null || detailJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(detailJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
