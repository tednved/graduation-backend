package com.graduation.backend.certification.api.dto;

import com.graduation.backend.common.domain.CertificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交认证申请。
 *
 * <p>只接受明文 {@code realName}/{@code studentNo}；脱敏展示值由服务端生成，
 * 请求里出现脱敏字段会因未知字段被直接判为 400。当前 MVP 只支持 {@code MANUAL}。
 */
public record SubmitCertificationRequest(
        @NotNull(message = "不能为空") CertificationType type,
        @NotBlank(message = "不能为空") @Size(min = 2, max = 30, message = "长度需在 2~30 之间") String realName,
        @NotBlank(message = "不能为空") @Size(min = 4, max = 32, message = "长度需在 4~32 之间") String studentNo) {
}
