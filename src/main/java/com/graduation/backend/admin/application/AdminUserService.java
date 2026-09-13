package com.graduation.backend.admin.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.backend.admin.api.dto.AdminUserResponse;
import com.graduation.backend.admin.query.AdminUserQueryMapper;
import com.graduation.backend.admin.query.AdminUserRow;
import com.graduation.backend.common.audit.AuditLogService;
import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.domain.UserStatus;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.common.web.LikePatterns;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.auth.application.RefreshTokenRevoker;
import com.graduation.backend.user.application.UserService;
import com.graduation.backend.user.domain.User;
import com.graduation.backend.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

/**
 * 管理端用户读写。
 *
 * <p>管理员身份在每个方法的事务内用 {@code UserService.requireAdmin} 重新判定：
 * 授权必须与写入同一事务、同一次读库，降权后不能靠旧 Token 继续管理。
 *
 * <p>禁用要同时撤销该用户全部 Refresh Token——只改状态的话，旧 Refresh Token 仍能换发
 * Access Token；撤销与状态变更同事务，不会出现「已禁用但还能登录」的中间态。
 * 恢复不自动恢复认证状态：认证需要重新提交审核。
 */
@Service
public class AdminUserService {

    private static final int KEYWORD_MAX = 50;
    private static final String TARGET_TYPE_USER = "USER";

    private final AdminUserQueryMapper adminUserQueryMapper;
    private final UserRepository userRepository;
    private final UserService userService;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public AdminUserService(AdminUserQueryMapper adminUserQueryMapper,
                            UserRepository userRepository,
                            UserService userService,
                            RefreshTokenRevoker refreshTokenRevoker,
                            AuditLogService auditLogService,
                            Clock clock) {
        this.adminUserQueryMapper = adminUserQueryMapper;
        this.userRepository = userRepository;
        this.userService = userService;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** 用户分页：可按昵称/手机号关键字、账号状态与认证状态筛选。 */
    @Transactional(readOnly = true)
    public PageResult<AdminUserResponse> list(Long adminId, String keyword, UserStatus status,
                                              CertificationStatus certificationStatus, int page, int size) {
        userService.requireAdmin(adminId);
        IPage<AdminUserRow> result = adminUserQueryMapper.selectUsers(
                new Page<>(page + 1L, size),
                LikePatterns.contains(keyword, KEYWORD_MAX),
                status == null ? null : status.name(),
                certificationStatus == null ? null : certificationStatus.name());
        return PageResult.of(page, size, result.getTotal(),
                result.getRecords().stream().map(AdminUserResponse::fromRow).toList());
    }

    /** 禁用用户并撤销其全部刷新令牌。 */
    @Transactional
    public AdminUserResponse disable(Long adminId, Long userId) {
        User admin = userService.requireAdmin(adminId);
        if (admin.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "不能禁用自己的账号");
        }
        User target = requireUserForUpdate(userId);
        target.disable(clock.instant());
        refreshTokenRevoker.revokeAllForUser(target.getId());
        auditLogService.record(admin.getId(), "USER_DISABLE", TARGET_TYPE_USER, target.getId(),
                Map.of("status", UserStatus.DISABLED.name()));
        return reload(target.getId());
    }

    /** 恢复用户为 ACTIVE，认证状态保持不变。 */
    @Transactional
    public AdminUserResponse enable(Long adminId, Long userId) {
        User admin = userService.requireAdmin(adminId);
        User target = requireUserForUpdate(userId);
        target.enable(clock.instant());
        auditLogService.record(admin.getId(), "USER_ENABLE", TARGET_TYPE_USER, target.getId(),
                Map.of("status", UserStatus.ACTIVE.name()));
        return reload(target.getId());
    }

    /**
     * 回显前先把改动刷盘。
     *
     * <p>下面的回显走 MyBatis，不会触发 JPA 的自动 flush；不显式 flush 就会把改动前的状态读回来。
     */
    private AdminUserResponse reload(Long userId) {
        userRepository.flush();
        return AdminUserResponse.fromRow(adminUserQueryMapper.selectById(userId));
    }

    private User requireUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
    }
}
