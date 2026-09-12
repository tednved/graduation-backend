package com.graduation.backend.item.api.dto;

import com.graduation.backend.category.domain.Category;

/** 契约 {@code CategoryRef}：只暴露 ID 与名称，用于商品卡片与详情。 */
public record CategoryRefResponse(Long id, String name) {

    public static CategoryRefResponse from(Category category) {
        return category == null ? null : new CategoryRefResponse(category.getId(), category.getName());
    }
}
