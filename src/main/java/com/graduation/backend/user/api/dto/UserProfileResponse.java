package com.graduation.backend.user.api.dto;

import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.domain.UserRole;
import com.graduation.backend.common.domain.UserStatus;
import com.graduation.backend.common.web.dto.CampusRefResponse;
import com.graduation.backend.user.domain.User;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 本人资料，仅 {@code GET /users/me} 与 {@code PATCH /users/me} 返回。
 *
 * <p>{@code reviewCount}、{@code version} 在契约里是 JSON number，故用 {@code Integer} 声明，
 * 否则会被全局 ID 序列化规则转成字符串。
 */
public record UserProfileResponse(
        Long id,
        UserRole role,
        UserStatus status,
        String nickname,
        String avatarUrl,
        String phone,
        CampusRefResponse campus,
        CertificationStatus certificationStatus,
        BigDecimal averageRating,
        Integer reviewCount,
        Instant createdAt,
        Instant lastLoginAt,
        Integer version) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getRole(),
                user.getStatus(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getPhone(),
                CampusRefResponse.from(user.getCampus()),
                user.getCertificationStatus(),
                user.getAverageRating(),
                user.getReviewCount(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getVersion() == null ? 0 : user.getVersion().intValue());
    }
}
