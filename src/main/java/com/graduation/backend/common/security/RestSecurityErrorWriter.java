package com.graduation.backend.common.security;

import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.common.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证与授权失败发生在 Spring Security 过滤链内，早于 {@code @RestControllerAdvice}，
 * 因此这里手动写出与其它接口同构的 {@link ApiError} 信封，避免前端收到空响应体或 HTML 登录页。
 */
@Component
public class RestSecurityErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(RestSecurityErrorWriter.class);

    public RestSecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        // 只记异常类型与原因，绝不记 Authorization 头或令牌本身。
        log.warn("认证失败 method={} path={} type={} reason={}",
                request.getMethod(), request.getRequestURI(),
                authException.getClass().getSimpleName(), authException.getMessage());
        write(response, ErrorCode.AUTH_UNAUTHORIZED, "未认证或凭证无效");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, ErrorCode.AUTH_FORBIDDEN, "没有执行该操作的权限");
    }

    private void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(code, message));
    }
}
