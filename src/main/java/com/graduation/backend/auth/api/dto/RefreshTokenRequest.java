package com.graduation.backend.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 刷新令牌请求。{@code refreshToken} 明文只在本次请求体里出现，服务端只比对哈希。 */
public record RefreshTokenRequest(
        @NotBlank(message = "不能为空") String refreshToken,
        @NotBlank(message = "不能为空") @Size(max = 64, message = "长度不能超过 64") String deviceId) {
}
