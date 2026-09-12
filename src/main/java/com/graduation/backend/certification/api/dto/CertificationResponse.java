package com.graduation.backend.certification.api.dto;

import com.graduation.backend.certification.domain.Certification;
import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.domain.CertificationType;
import com.graduation.backend.common.web.dto.CampusRefResponse;

import java.time.Instant;

/**
 * 认证申请。
 *
 * <p>只返回脱敏后的姓名与学号，不返回审核人身份、证据文件或任何内部路径。
 */
public record CertificationResponse(
        Long id,
        Long userId,
        CampusRefResponse campus,
        CertificationType type,
        String realNameMasked,
        String studentNoMasked,
        CertificationStatus status,
        String rejectReason,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static CertificationResponse from(Certification certification, CampusRefResponse campus) {
        return new CertificationResponse(
                certification.getId(),
                certification.getUserId(),
                campus,
                certification.getType(),
                certification.getRealNameMasked(),
                certification.getStudentNoMasked(),
                certification.getStatus(),
                certification.getRejectReason(),
                certification.getReviewedAt(),
                certification.getCreatedAt(),
                certification.getUpdatedAt());
    }
}
