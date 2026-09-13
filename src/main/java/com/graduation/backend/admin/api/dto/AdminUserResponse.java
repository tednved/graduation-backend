package com.graduation.backend.admin.api.dto;

import com.graduation.backend.admin.query.AdminUserRow;
import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.domain.UserRole;
import com.graduation.backend.common.domain.UserStatus;
import com.graduation.backend.item.api.dto.CampusRefResponse;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 管理端用户条目。
 *
 * <p>契约明文要求「即使对管理员也不返回 openid」：这里的字段全部来自投影查询，
 * SQL 里就没有 openid 列，不存在漏返回的可能。
 */
public record AdminUserResponse(
        Long id,
        String nickname,
        String avatarUrl,
        String phone,
        UserRole role,
        UserStatus status,
        CampusRefResponse campus,
        CertificationStatus certificationStatus,
        BigDecimal averageRating,
        Integer reviewCount,
        Instant createdAt,
        Instant lastLoginAt) {

    public static AdminUserResponse fromRow(AdminUserRow row) {
        return new AdminUserResponse(
                row.getId(),
                row.getNickname(),
                row.getAvatarUrl(),
                row.getPhone(),
                row.getRole(),
                row.getStatus(),
                row.getCampusId() == null ? null : new CampusRefResponse(row.getCampusId(), row.getCampusName()),
                row.getCertificationStatus(),
                row.getAverageRating(),
                row.getReviewCount(),
                row.getCreatedAt(),
                row.getLastLoginAt());
    }
}
