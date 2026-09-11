package com.graduation.backend.common.web.dto;

import com.graduation.backend.common.domain.Campus;

/** 校区引用，只暴露 ID 与名称。 */
public record CampusRefResponse(Long id, String name) {

    public static CampusRefResponse from(Campus campus) {
        return campus == null ? null : new CampusRefResponse(campus.getId(), campus.getName());
    }
}
