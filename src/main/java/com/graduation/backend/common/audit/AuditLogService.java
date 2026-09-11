package com.graduation.backend.common.audit;

import com.graduation.backend.common.web.RequestIds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Map;

/**
 * 管理操作审计写入。
 *
 * <p>强制要求调用方已经开启事务：审计与业务状态变更要么一起提交、要么一起回滚，
 * 不允许出现「改了状态却没有审计记录」的中间态。
 */
@Service
public class AuditLogService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditLogService(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long operatorId, String action, String targetType, Long targetId, Map<String, Object> detail) {
        String detailJson = (detail == null || detail.isEmpty())
                ? "{}"
                : objectMapper.writeValueAsString(detail);
        repository.save(AuditLog.of(
                operatorId, action, targetType, targetId, RequestIds.current(), detailJson, clock.instant()));
    }
}
