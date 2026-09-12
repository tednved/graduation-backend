package com.graduation.backend.user.api;

import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.user.api.dto.PublicUserResponse;
import com.graduation.backend.user.api.dto.UpdateProfileRequest;
import com.graduation.backend.user.api.dto.UserProfileResponse;
import com.graduation.backend.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户资料接口。当前用户 ID 取自安全上下文，路径与请求体都不接受用户身份字段。 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserController(UserService userService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> myProfile() {
        return ApiResponse.ok(userService.profile(currentUserService.requireCurrentUserId()));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(currentUserService.requireCurrentUserId(), request));
    }

    @GetMapping("/{userId}/public")
    public ApiResponse<PublicUserResponse> publicProfile(@PathVariable Long userId) {
        return ApiResponse.ok(userService.publicProfile(userId));
    }
}
