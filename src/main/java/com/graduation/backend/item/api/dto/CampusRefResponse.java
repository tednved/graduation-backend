package com.graduation.backend.item.api.dto;

import com.graduation.backend.common.domain.Campus;

/** 契约 {@code CampusRef}：只暴露 ID 与名称。单校区 MVP 里恒为 {@code MAIN}。 */
public record CampusRefResponse(Long id, String name) {

    public static CampusRefResponse from(Campus campus) {
        return campus == null ? null : new CampusRefResponse(campus.getId(), campus.getName());
    }
}
