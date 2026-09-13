package com.graduation.backend.admin.query;

import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.domain.UserRole;
import com.graduation.backend.common.domain.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 管理端用户行（MyBatis 直接映射）。
 *
 * <p>刻意不含 {@code openid}：管理端响应不返回登录标识，SQL 里就不查这一列。
 */
public class AdminUserRow {

    private Long id;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private UserRole role;
    private UserStatus status;
    private Long campusId;
    private String campusName;
    private CertificationStatus certificationStatus;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Instant createdAt;
    private Instant lastLoginAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Long getCampusId() {
        return campusId;
    }

    public void setCampusId(Long campusId) {
        this.campusId = campusId;
    }

    public String getCampusName() {
        return campusName;
    }

    public void setCampusName(String campusName) {
        this.campusName = campusName;
    }

    public CertificationStatus getCertificationStatus() {
        return certificationStatus;
    }

    public void setCertificationStatus(CertificationStatus certificationStatus) {
        this.certificationStatus = certificationStatus;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(BigDecimal averageRating) {
        this.averageRating = averageRating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
