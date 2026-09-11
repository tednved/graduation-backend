package com.graduation.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * §7.2 固定错误码全集。每个错误码绑定唯一的 HTTP 状态，OpenAPI 契约中
 * {@code ErrorNNN} 响应组件的 {@code x-error-codes} 必须与本枚举一致。
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    FILE_INVALID_TYPE(HttpStatus.BAD_REQUEST),

    AUTH_INVALID_CODE(HttpStatus.UNAUTHORIZED),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_INVALID(HttpStatus.UNAUTHORIZED),

    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN),
    USER_DISABLED(HttpStatus.FORBIDDEN),
    USER_CERTIFICATION_REQUIRED(HttpStatus.FORBIDDEN),
    FILE_NOT_OWNED(HttpStatus.FORBIDDEN),
    ORDER_OPERATION_FORBIDDEN(HttpStatus.FORBIDDEN),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    CERTIFICATION_PENDING_EXISTS(HttpStatus.CONFLICT),
    CERTIFICATION_ALREADY_REVIEWED(HttpStatus.CONFLICT),
    CATEGORY_IN_USE(HttpStatus.CONFLICT),
    ITEM_NOT_EDITABLE(HttpStatus.CONFLICT),
    ITEM_NOT_AVAILABLE(HttpStatus.CONFLICT),
    ITEM_SELF_PURCHASE(HttpStatus.CONFLICT),
    ITEM_CONCURRENTLY_RESERVED(HttpStatus.CONFLICT),
    ITEM_SELF_OPERATION(HttpStatus.CONFLICT),
    ORDER_ILLEGAL_STATUS_TRANSITION(HttpStatus.CONFLICT),
    ORDER_DUPLICATE_REQUEST(HttpStatus.CONFLICT),
    REVIEW_NOT_ALLOWED(HttpStatus.CONFLICT),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT),

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
