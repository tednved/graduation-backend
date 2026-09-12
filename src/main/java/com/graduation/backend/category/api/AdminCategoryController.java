package com.graduation.backend.category.api;

import com.graduation.backend.category.api.dto.CategoryResponse;
import com.graduation.backend.category.api.dto.CreateCategoryRequest;
import com.graduation.backend.category.api.dto.UpdateCategoryRequest;
import com.graduation.backend.category.application.CategoryService;
import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 分类管理接口。管理员身份在这里校验，业务规则与审计写入在 {@link CategoryService}。 */
@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final UserService userService;
    private final CurrentUserService currentUserService;

    public AdminCategoryController(CategoryService categoryService, UserService userService,
                                   CurrentUserService currentUserService) {
        this.categoryService = categoryService;
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.ok(categoryService.create(operatorId(), request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.ok(categoryService.update(operatorId(), id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<CategoryResponse> enable(@PathVariable Long id) {
        return ApiResponse.ok(categoryService.enable(operatorId(), id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<CategoryResponse> disable(@PathVariable Long id) {
        return ApiResponse.ok(categoryService.disable(operatorId(), id));
    }

    private Long operatorId() {
        return userService.requireAdmin(currentUserService.requireCurrentUserId()).getId();
    }
}
