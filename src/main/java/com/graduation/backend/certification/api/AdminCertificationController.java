package com.graduation.backend.certification.api;

import com.graduation.backend.certification.api.dto.CertificationResponse;
import com.graduation.backend.certification.api.dto.RejectCertificationRequest;
import com.graduation.backend.certification.application.CertificationService;
import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.user.application.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端认证审核。
 *
 * <p>管理员身份每次请求都重新读库判断，不做缓存：降权立即生效。
 */
@RestController
@RequestMapping("/api/v1/admin/certifications")
@Validated
public class AdminCertificationController {

    private final CertificationService certificationService;
    private final UserService userService;
    private final CurrentUserService currentUserService;

    public AdminCertificationController(CertificationService certificationService,
                                        UserService userService,
                                        CurrentUserService currentUserService) {
        this.certificationService = certificationService;
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<PageResult<CertificationResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) CertificationStatus status) {
        requireAdmin();
        return ApiResponse.ok(certificationService.adminList(page, size, status));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<CertificationResponse> approve(@PathVariable("id") Long id) {
        return ApiResponse.ok(certificationService.approve(requireAdmin(), id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<CertificationResponse> reject(@PathVariable("id") Long id,
                                                     @Valid @RequestBody RejectCertificationRequest request) {
        return ApiResponse.ok(certificationService.reject(requireAdmin(), id, request.reason()));
    }

    /** 控制器只做参数到用例的转接，权限判定与写入都在服务层事务内完成。 */
    private Long requireAdmin() {
        return userService.requireAdmin(currentUserService.requireCurrentUserId()).getId();
    }
}
