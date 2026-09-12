package com.graduation.backend.user.api.dto;

import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.user.domain.User;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 公开脱敏资料。只返回契约允许的字段：没有 openid、手机号、认证证据或校区 ID。
 * 被禁用用户的历史评价仍可查看，因此这里用 {@code accountActive=false} 而不是 404。
 */
public record PublicUserResponse(
        Long id,
        String nickname,
        String avatarUrl,
        String campusName,
        CertificationStatus certificationStatus,
        BigDecimal averageRating,
        Integer reviewCount,
        boolean accountActive,
        Instant createdAt) {

    public static PublicUserResponse from(User user) {
        return new PublicUserResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getCampus() == null ? null : user.getCampus().getName(),
                user.getCertificationStatus(),
                user.getAverageRating(),
                user.getReviewCount(),
                user.isActive(),
                user.getCreatedAt());
    }
}
