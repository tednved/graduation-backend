package com.graduation.backend.common.web;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * 把所有失败响应统一包裹成 {@link ApiError}。
 *
 * <p>继承 {@link ResponseEntityExceptionHandler} 而不是逐个列举 Spring MVC 异常，
 * 这样 405/415 之类的框架异常也会被包成信封，不会出现空响应体。
 *
 * <p>响应中的 {@code message} 只做粗粒度说明，绝不包含堆栈、SQL、文件路径或 Token；
 * 详细原因只写服务端日志。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.code();
        ApiError body = ApiError.of(code, ex.getMessage());
        if (code.status().is5xxServerError()) {
            log.error("业务异常 [{}] requestId={}", code, body.requestId(), ex);
        } else {
            log.warn("业务异常 [{}] requestId={} message={}", code, body.requestId(), ex.getMessage());
        }
        return ResponseEntity.status(code.status()).body(body);
    }

    /**
     * multipart 解析失败属于请求格式问题，按 400 处理。
     *
     * <p>超限（{@code MaxUploadSizeExceededException}）不在这里：框架基类的
     * {@code handleException} 已经覆盖该类型并给出 413，由 {@link #handleExceptionInternal}
     * 映射成 {@code FILE_TOO_LARGE}；重复映射会触发「同一异常类型多个处理方法的歧义」启动错误。
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleMultipart(MultipartException ex) {
        log.warn("multipart 解析失败 requestId={} type={}", RequestIds.current(), ex.getClass().getSimpleName());
        return badRequest("上传请求格式不合法");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return badRequest(detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        ApiError body = ApiError.of(ErrorCode.INTERNAL_ERROR, "服务暂时不可用，请稍后再试");
        log.error("未预期异常 requestId={}", body.requestId(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .collect(Collectors.joining("; "));
        if (detail.isBlank()) {
            detail = "请求参数不合法";
        }
        return envelope(ErrorCode.VALIDATION_ERROR, detail, headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ErrorCode code = mapStatus(status);
        if (code.status().is5xxServerError()) {
            log.error("请求处理失败 requestId={} status={}", RequestIds.current(), status.value(), ex);
        } else {
            log.warn("请求被拒绝 requestId={} status={} type={}",
                    RequestIds.current(), status.value(), ex.getClass().getSimpleName());
        }
        return envelope(code, defaultMessage(code), headers);
    }

    private ResponseEntity<Object> envelope(ErrorCode code, String message, HttpHeaders headers) {
        ApiError error = ApiError.of(code, message);
        return new ResponseEntity<>(error, headers, code.status());
    }

    private ResponseEntity<ApiError> badRequest(String message) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiError.of(ErrorCode.VALIDATION_ERROR, message));
    }

    private String describe(FieldError error) {
        String message = error.getDefaultMessage();
        return error.getField() + " " + (message == null ? "不合法" : message);
    }

    /**
     * 框架异常按状态映射到已定义的错误码，并始终以该错误码自己的 HTTP 状态返回，
     * 保证「错误码 ↔ HTTP 状态」在运行时也是 1:1，不产生契约外的组合。
     */
    private ErrorCode mapStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCode.AUTH_UNAUTHORIZED;
            case 403 -> ErrorCode.AUTH_FORBIDDEN;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 413 -> ErrorCode.FILE_TOO_LARGE;
            case 400, 405, 406, 415, 422 -> ErrorCode.VALIDATION_ERROR;
            default -> status.is4xxClientError() ? ErrorCode.VALIDATION_ERROR : ErrorCode.INTERNAL_ERROR;
        };
    }

    private String defaultMessage(ErrorCode code) {
        return switch (code) {
            case VALIDATION_ERROR -> "请求参数不合法";
            case AUTH_UNAUTHORIZED -> "未认证或凭证无效";
            case AUTH_FORBIDDEN -> "没有执行该操作的权限";
            case RESOURCE_NOT_FOUND -> "请求的资源不存在";
            case FILE_TOO_LARGE -> "上传文件超过大小上限";
            default -> "服务暂时不可用，请稍后再试";
        };
    }
}
