package com.graduation.backend.common.web;

import com.graduation.backend.common.error.ErrorCode;

import java.time.Instant;

/**
 * 失败响应信封。{@code data} 固定为 {@code null}；HTTP 状态由 {@link ErrorCode} 决定。
 * {@code message} 必须是可以直接展示的说明，绝不包含堆栈、SQL、路径或敏感值。
 */
public record ApiError(String code, String message, Object data, String requestId, Instant timestamp) {

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.name(), message, null, RequestIds.current(), Instant.now());
    }
}
