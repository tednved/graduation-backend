package com.graduation.backend.category.api;

import com.graduation.backend.category.api.dto.CategoryTreeResponse;
import com.graduation.backend.category.application.CategoryService;
import com.graduation.backend.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开分类接口。只暴露启用节点，无需登录。 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/tree")
    public ApiResponse<CategoryTreeResponse> tree() {
        return ApiResponse.ok(categoryService.tree());
    }
}
