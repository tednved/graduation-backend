package com.graduation.backend.category.api.dto;

import java.util.List;

/** 两级分类树，只包含启用节点。 */
public record CategoryTreeResponse(List<CategoryNodeResponse> categories) {
}
