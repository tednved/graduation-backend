package com.graduation.backend.category.api.dto;

import com.graduation.backend.category.domain.Category;
import com.graduation.backend.common.domain.CategoryStatus;

/**
 * 分类树二级节点。契约明确二级节点不返回 {@code children}，因此用独立类型而不是把
 * 一级节点的 {@code children} 置空 —— 后者在 JSON 里会留下一个语义不明的 {@code null}。
 */
public record CategoryLeafResponse(
        Long id,
        Long parentId,
        String name,
        String iconUrl,
        Integer sortNo,
        CategoryStatus status) {

    public static CategoryLeafResponse from(Category category) {
        return new CategoryLeafResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getIconUrl(),
                category.getSortNo(),
                category.getStatus());
    }
}
