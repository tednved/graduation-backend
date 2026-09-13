package com.graduation.backend.admin.query;

import java.time.Instant;

/**
 * 审计日志行（MyBatis 直接映射）。
 *
 * <p>{@code detailJson} 在写入时就已脱敏（不含密钥、Token、会话信息、完整手机号与学号），
 * 这里原样取出，由响应层解析成对象。
 */
public class AuditLogRow {

    private Long id;
    private Long operatorId;
    private String operatorNickname;
    private String operatorAvatarUrl;
    private String action;
    private String targetType;
    private Long targetId;
    private String requestId;
    private String detailJson;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorNickname() {
        return operatorNickname;
    }

    public void setOperatorNickname(String operatorNickname) {
        this.operatorNickname = operatorNickname;
    }

    public String getOperatorAvatarUrl() {
        return operatorAvatarUrl;
    }

    public void setOperatorAvatarUrl(String operatorAvatarUrl) {
        this.operatorAvatarUrl = operatorAvatarUrl;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
