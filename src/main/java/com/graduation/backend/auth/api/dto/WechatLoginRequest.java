package com.graduation.backend.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 微信登录请求。{@code code} 只用于当次换取会话，不落库、不写日志。 */
public record WechatLoginRequest(
        @NotBlank(message = "不能为空") String code,
        @NotBlank(message = "不能为空") @Size(max = 64, message = "长度不能超过 64") String deviceId) {
}
