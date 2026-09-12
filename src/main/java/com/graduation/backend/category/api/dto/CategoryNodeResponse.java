package com.graduation.backend.category.api.dto;

import com.graduation.backend.category.domain.Category;
import com.graduation.backend.common.domain.CategoryStatus;

import java.util.List;

/** 分类树一级节点。始终包含 {@code children}，没有启用子分类时为空数组。 */
public record CategoryNodeResponse(
        Long id,
        Long parentId,
        String name,
        String iconUrl,
        Integer sortNo,
        CategoryStatus status,
        List<CategoryLeafResponse> children) {

    public static CategoryNodeResponse from(Category category, List<CategoryLeafResponse> children) {
        return new CategoryNodeResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getIconUrl(),
                category.getSortNo(),
                category.getStatus(),
                children);
    }
}
