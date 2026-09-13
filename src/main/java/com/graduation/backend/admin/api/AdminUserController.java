package com.graduation.backend.admin.api;

import com.graduation.backend.admin.api.dto.AdminUserResponse;
import com.graduation.backend.admin.application.AdminUserService;
import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.domain.UserStatus;
import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.common.web.PageResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户接口。
 *
 * <p>管理员身份在服务层事务内判定（{@code UserService.requireAdmin}），不引入
 * {@code @PreAuthorize}，与既有的 {@code AdminItemController} 保持一致：
 * 授权必须与写入同一事务、同一次读库，降权后不能靠旧 Token 继续管理。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Validated
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final CurrentUserService currentUserService;

    public AdminUserController(AdminUserService adminUserService, CurrentUserService currentUserService) {
        this.adminUserService = adminUserService;
        this.currentUserService = currentUserService;
    }

    /** 用户分页：按关键字、账号状态、认证状态筛选。 */
    @GetMapping
    public ApiResponse<PageResult<AdminUserResponse>> list(
            @RequestParam(required = false) @Size(min = 1, max = 50) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) CertificationStatus certificationStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(adminUserService.list(
                currentUserId(), keyword, status, certificationStatus, page, size));
    }

    /** 禁用用户：禁止操作自己，同时撤销其全部刷新令牌。 */
    @PostMapping("/{id}/disable")
    public ApiResponse<AdminUserResponse> disable(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminUserService.disable(currentUserId(), id));
    }

    /** 恢复用户为 ACTIVE，认证状态保持不变。 */
    @PostMapping("/{id}/enable")
    public ApiResponse<AdminUserResponse> enable(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminUserService.enable(currentUserId(), id));
    }

    private Long currentUserId() {
        return currentUserService.requireCurrentUserId();
    }
}
