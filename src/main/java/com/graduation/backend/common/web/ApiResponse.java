package com.graduation.backend.common.web;

import java.time.Instant;

/**
 * 成功响应信封。{@code code} 固定为 {@code OK}，业务差异由 HTTP 状态与 {@code data} 表达。
 * {@code timestamp} 交给全局 Jackson 序列化器格式化为 UTC ISO 8601 毫秒。
 */
public record ApiResponse<T>(String code, String message, T data, String requestId, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data, RequestIds.current(), Instant.now());
    }
}
