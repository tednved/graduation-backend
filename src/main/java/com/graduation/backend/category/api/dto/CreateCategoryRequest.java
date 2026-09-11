package com.graduation.backend.category.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建分类。{@code parentId} 为空表示创建一级分类。 */
public record CreateCategoryRequest(
        @NotBlank(message = "不能为空") @Size(max = 20, message = "长度不能超过 20") String name,
        Long parentId,
        String iconUrl,
        @NotNull(message = "不能为空") @Min(value = 0, message = "不能小于 0")
        @Max(value = 9999, message = "不能大于 9999") Integer sortNo) {
}
