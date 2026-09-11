package com.graduation.backend.certification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 驳回认证申请，原因必填 2~200 字。 */
public record RejectCertificationRequest(
        @NotBlank(message = "不能为空") @Size(min = 2, max = 200, message = "长度需在 2~200 之间") String reason) {
}
