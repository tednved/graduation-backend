package com.graduation.backend.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 退出登录请求。已撤销或不存在的令牌同样返回 204，保证重复提交幂等。 */
public record LogoutRequest(@NotBlank(message = "不能为空") String refreshToken) {
}
