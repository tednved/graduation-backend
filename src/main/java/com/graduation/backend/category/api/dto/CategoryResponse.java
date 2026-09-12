package com.graduation.backend.category.api.dto;

import com.graduation.backend.category.domain.Category;
import com.graduation.backend.common.domain.CategoryStatus;

import java.time.Instant;

/** 管理接口返回的分类完整结构。 */
public record CategoryResponse(
        Long id,
        Long parentId,
        String name,
        String iconUrl,
        Integer sortNo,
        CategoryStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getIconUrl(),
                category.getSortNo(),
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
