package com.graduation.backend.user.application;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.file.application.FileService;
import com.graduation.backend.file.config.FileProperties;
import com.graduation.backend.file.domain.FileObject;
import com.graduation.backend.user.api.dto.PublicUserResponse;
import com.graduation.backend.user.api.dto.UpdateProfileRequest;
import com.graduation.backend.user.api.dto.UserProfileResponse;
import com.graduation.backend.user.domain.User;
import com.graduation.backend.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 用户资料的读写边界。
 *
 * <p>授权判断（是否 ACTIVE、是否管理员）在这里基于库内最新状态进行，并且与写入处于同一事务，
 * 拿到的一定是受管实体：控制器传入的只是用户 ID。
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final FileService fileService;
    private final FileProperties fileProperties;
    private final Clock clock;

    public UserService(UserRepository userRepository, FileService fileService,
                       FileProperties fileProperties, Clock clock) {
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.fileProperties = fileProperties;
        this.clock = clock;
    }

    /**
     * 在调用方事务内加载并校验账号状态。
     *
     * <p>不加 {@code @Transactional}：它在调用方事务中参与执行，从而返回受管实体；
     * 独立调用时由仓库自带的事务兜底。
     */
    public User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "登录状态已失效，请重新登录"));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_DISABLED, "账号已被禁用，请联系管理员");
        }
        return user;
    }

    /** 按主键加载用户，不校验账号状态：供内部同步认证状态等场景使用（申请人可能已被禁用）。 */
    public User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
    }

    public User requireAdmin(Long userId) {
        User user = requireActiveUser(userId);
        if (!user.isAdmin()) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "需要管理员权限");
        }
        return user;
    }

    /**
     * 悲观锁定并校验账号状态。
     *
     * <p>供需要「同一用户串行化」的写入使用，例如认证申请的唯一 PENDING 约束。
     */
    public User requireActiveUserForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "登录状态已失效，请重新登录"));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_DISABLED, "账号已被禁用，请联系管理员");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(Long userId) {
        return UserProfileResponse.from(requireActiveUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireActiveUser(userId);
        Instant now = clock.instant();

        if (request.hasNickname()) {
            user.rename(request.nicknameTrimmed(), now);
        }
        if (request.hasPhone()) {
            user.changePhone(request.getPhone(), now);
        }
        if (request.hasAvatarFileId()) {
            if (request.getAvatarFileId() == null) {
                user.changeAvatar(null, now);
            } else {
                FileObject avatar = fileService.requireBindableAvatar(request.getAvatarFileId(), user.getId());
                avatar.bind(now);
                user.changeAvatar(fileProperties.publicUrlFor(avatar.getId()), now);
            }
        }
        return UserProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PublicUserResponse publicProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        return PublicUserResponse.from(user);
    }
}
