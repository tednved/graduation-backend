package com.graduation.backend.certification.api.dto;

import com.graduation.backend.common.domain.CertificationStatus;

/**
 * 本人最新认证申请。
 *
 * <p>从未提交过申请时 {@code status = NOT_SUBMITTED}、{@code application = null}，
 * 契约明确要求这种情形返回 200 而不是 404。
 */
public record CertificationLatestResponse(CertificationStatus status, CertificationResponse application) {

    public static CertificationLatestResponse notSubmitted() {
        return new CertificationLatestResponse(CertificationStatus.NOT_SUBMITTED, null);
    }
}
