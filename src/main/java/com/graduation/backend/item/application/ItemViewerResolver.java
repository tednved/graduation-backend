package com.graduation.backend.item.application;

import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.user.application.UserService;
import com.graduation.backend.user.domain.User;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 解析「当前查看者」。
 *
 * <p>商品详情是契约里少见的匿名可访问接口，同时又要返回 {@code favorited}/{@code isOwner}/{@code canBuy}
 * 这类个性化字段，因此需要区分「匿名」与「已登录」而不是直接 {@code requireCurrentUserId()}。
 *
 * <p>已有 Token 但账号不可用时不做匿名降级：调用方明确出示了身份，静默当作匿名会让本人看不到
 * 自己的草稿（404），比直接返回 403 {@code USER_DISABLED} 更难排查。因此这里让
 * {@code requireActiveUser} 的异常照常抛出。
 */
@Component
public class ItemViewerResolver {

    private final CurrentUserService currentUserService;
    private final UserService userService;

    public ItemViewerResolver(CurrentUserService currentUserService, UserService userService) {
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    /** 匿名返回 {@link Optional#empty()}，不做任何数据库访问。 */
    public Optional<Viewer> resolve() {
        return currentUserId().map(userId -> Viewer.of(userService.requireActiveUser(userId)));
    }

    /** 需要登录的接口使用：匿名或凭证无效由 {@code CurrentUserService} 抛 401。 */
    public Viewer require() {
        return Viewer.of(userService.requireActiveUser(currentUserService.requireCurrentUserId()));
    }

    private Optional<Long> currentUserId() {
        try {
            return Optional.of(currentUserService.requireCurrentUserId());
        } catch (BusinessException ex) {
            // 无 Token / Token 无效：本接口允许匿名，不作为错误。
            return Optional.empty();
        }
    }

    /** 查看者上下文：管理员与认证状态都取自已加载的用户实体。 */
    public record Viewer(Long userId, boolean admin, boolean certified) {

        public static Viewer of(User user) {
            return new Viewer(
                    user.getId(),
                    user.isAdmin(),
                    user.getCertificationStatus() == CertificationStatus.APPROVED);
        }
    }
}
