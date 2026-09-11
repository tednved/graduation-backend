package com.graduation.backend.certification.query;

import java.time.Instant;

/**
 * 管理端认证列表的查询行。
 *
 * <p>刻意不是 JPA 实体：列表查询走 MyBatis-Plus（条件筛选 + 物理分页），写操作走 JPA，
 * 两者作用在同一张表但不会同时更新同一行。
 */
public class CertificationListRow {

    private Long id;
    private Long userId;
    private Long campusId;
    private String type;
    private String realNameMasked;
    private String studentNoMasked;
    private String status;
    private String rejectReason;
    private Instant reviewedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getCampusId() {
        return campusId;
    }

    public void setCampusId(Long campusId) {
        this.campusId = campusId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRealNameMasked() {
        return realNameMasked;
    }

    public void setRealNameMasked(String realNameMasked) {
        this.realNameMasked = realNameMasked;
    }

    public String getStudentNoMasked() {
        return studentNoMasked;
    }

    public void setStudentNoMasked(String studentNoMasked) {
        this.studentNoMasked = studentNoMasked;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
