package com.graduation.backend.user.domain;

import com.graduation.backend.common.domain.Campus;
import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.domain.UserRole;
import com.graduation.backend.common.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * 用户账号与信用汇总。
 *
 * <p>{@code openid} 只用于登录识别，绝不出现在任何响应、日志或审计中。
 * 所有状态变化都通过领域方法进行，控制器与查询不得直接改字段。
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "openid", nullable = false, length = 64)
    private String openid;

    @Column(name = "unionid", length = 64)
    private String unionid;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "nickname", nullable = false, length = 30)
    private String nickname;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "phone", length = 32)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id")
    private Campus campus;

    @Enumerated(EnumType.STRING)
    @Column(name = "certification_status", nullable = false, length = 32)
    private CertificationStatus certificationStatus;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
    }

    private User(String openid, String nickname, Campus campus, Instant now) {
        this.openid = openid;
        this.nickname = nickname;
        this.campus = campus;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
        this.certificationStatus = CertificationStatus.NOT_SUBMITTED;
        this.averageRating = BigDecimal.ZERO.setScale(2);
        this.reviewCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 首次登录时创建默认 USER/ACTIVE 用户。 */
    public static User register(String openid, String nickname, Campus campus, Instant now) {
        return new User(openid, nickname, campus, now);
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    public void rename(String nickname, Instant now) {
        this.nickname = nickname;
        this.updatedAt = now;
    }

    public void changePhone(String phone, Instant now) {
        this.phone = phone;
        this.updatedAt = now;
    }

    public void changeAvatar(String avatarUrl, Instant now) {
        this.avatarUrl = avatarUrl;
        this.updatedAt = now;
    }

    public void changeCertificationStatus(CertificationStatus next, Instant now) {
        this.certificationStatus = next;
        this.updatedAt = now;
    }

    /** 资料校区不支持用户自行修改，仅由认证审核等内部流程绑定。 */
    public void bindCampus(Campus campus, Instant now) {
        this.campus = campus;
        this.updatedAt = now;
    }

    /**
     * 用评价模块算出的聚合值覆盖信用汇总。
     *
     * <p>聚合值由数据库按 {@code VISIBLE} 评价算出，这里只负责落库：
     * 平均分统一保留两位小数（与列定义 {@code DECIMAL(3,2)} 一致），没有评价时是 0.00 而不是 null。
     */
    public void applyRating(BigDecimal averageRating, long reviewCount, Instant now) {
        this.averageRating = averageRating == null
                ? BigDecimal.ZERO.setScale(2)
                : averageRating.setScale(2, RoundingMode.HALF_UP);
        this.reviewCount = Math.toIntExact(reviewCount);
        this.updatedAt = now;
    }

    /** 管理员禁用账号：已有 Token 的请求会在各服务层被 {@code USER_DISABLED} 拦下。 */
    public void disable(Instant now) {
        this.status = UserStatus.DISABLED;
        this.updatedAt = now;
    }

    /** 管理员恢复账号。 */
    public void enable(Instant now) {
        this.status = UserStatus.ACTIVE;
        this.updatedAt = now;
    }

    public boolean isDisabled() {
        return status == UserStatus.DISABLED;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getOpenid() {
        return openid;
    }

    public String getUnionid() {
        return unionid;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getPhone() {
        return phone;
    }

    public Campus getCampus() {
        return campus;
    }

    public CertificationStatus getCertificationStatus() {
        return certificationStatus;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
