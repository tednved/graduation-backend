package com.graduation.backend.common.error;

/**
 * 业务异常。{@code message} 会出现在失败响应的 {@code message} 字段中，
 * 因此必须是可以直接给终端用户看的说明，不得包含堆栈、SQL、路径、Token 或任何敏感值。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
