package com.graduation.backend.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 管理操作的审计记录。
 *
 * <p>{@code detailJson} 只允许写状态迁移这类可公开的上下文，绝不写入明文姓名、学号、
 * 手机号、Token 或文件内部路径。
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", nullable = false)
    private String detailJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    private AuditLog(Long operatorId, String action, String targetType, Long targetId,
                     String requestId, String detailJson, Instant createdAt) {
        this.operatorId = operatorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestId = requestId;
        this.detailJson = detailJson;
        this.createdAt = createdAt;
    }

    public static AuditLog of(Long operatorId, String action, String targetType, Long targetId,
                              String requestId, String detailJson, Instant createdAt) {
        return new AuditLog(operatorId, action, targetType, targetId, requestId, detailJson, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
