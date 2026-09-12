package com.graduation.backend.certification.domain;

import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.domain.CertificationType;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 校园认证申请。
 *
 * <p>库里只保存脱敏后的姓名与学号，明文在提交请求处理完即丢弃：既不入库、不写日志，也不进审计。
 * 状态迁移只允许 PENDING → APPROVED / REJECTED，重复审核由领域方法拒绝。
 */
@Entity
@Table(name = "certifications")
public class Certification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private CertificationType type;

    @Column(name = "real_name_masked", nullable = false, length = 80)
    private String realNameMasked;

    @Column(name = "student_no_masked", length = 64)
    private String studentNoMasked;

    @Column(name = "evidence_file_id")
    private Long evidenceFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CertificationStatus status;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Certification() {
    }

    public static Certification submit(Long userId, Long campusId, CertificationType type,
                                      String realNameMasked, String studentNoMasked, Instant now) {
        Certification certification = new Certification();
        certification.userId = userId;
        certification.campusId = campusId;
        certification.type = type;
        certification.realNameMasked = realNameMasked;
        certification.studentNoMasked = studentNoMasked;
        certification.status = CertificationStatus.PENDING;
        certification.createdAt = now;
        certification.updatedAt = now;
        return certification;
    }

    public void approve(Long reviewerId, Instant now) {
        requirePending();
        this.status = CertificationStatus.APPROVED;
        this.reviewerId = reviewerId;
        this.reviewedAt = now;
        this.rejectReason = null;
        this.updatedAt = now;
    }

    public void reject(Long reviewerId, String reason, Instant now) {
        requirePending();
        this.status = CertificationStatus.REJECTED;
        this.reviewerId = reviewerId;
        this.reviewedAt = now;
        this.rejectReason = reason;
        this.updatedAt = now;
    }

    public boolean isPending() {
        return status == CertificationStatus.PENDING;
    }

    private void requirePending() {
        if (status != CertificationStatus.PENDING) {
            throw new BusinessException(ErrorCode.CERTIFICATION_ALREADY_REVIEWED, "该申请已审核完成，不能重复审核");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCampusId() {
        return campusId;
    }

    public CertificationType getType() {
        return type;
    }

    public String getRealNameMasked() {
        return realNameMasked;
    }

    public String getStudentNoMasked() {
        return studentNoMasked;
    }

    public Long getEvidenceFileId() {
        return evidenceFileId;
    }

    public CertificationStatus getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
