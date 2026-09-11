package com.graduation.backend.auth.api.dto;

import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.web.dto.UserSummaryResponse;

/**
 * 令牌对与当前用户摘要。
 *
 * <p>{@code expiresIn}/{@code refreshExpiresIn} 契约里是 JSON number（非 ID），
 * 因此必须用 {@code Integer} 声明，否则会被全局 ID 序列化规则误转成字符串。
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Integer expiresIn,
        Integer refreshExpiresIn,
        UserSummaryResponse user,
        CertificationStatus certificationStatus) {

    public static final String BEARER = "Bearer";
}
