package com.graduation.backend.common.web.dto;

import com.graduation.backend.user.domain.User;

/** 嵌入其它资源的用户精简摘要，永不包含 openid、手机号或认证信息。 */
public record UserSummaryResponse(Long id, String nickname, String avatarUrl) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getNickname(), user.getAvatarUrl());
    }
}
