package com.graduation.backend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 解析并贯通 {@code X-Request-Id}。
 *
 * <p>只接受 1～64 个 ASCII 字母、数字、下划线或连字符；缺失或非法时服务端重新生成，
 * 不因此返回错误。MDC 在 {@code finally} 中清理，避免线程复用时串号。
 *
 * <p>顺序早于 Spring Security 过滤链，保证被拒绝的请求也能带上 requestId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Pattern ACCEPTED = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(RequestIds.HEADER);
        String requestId = (incoming != null && ACCEPTED.matcher(incoming).matches())
                ? incoming
                : RequestIds.generate();
        MDC.put(RequestIds.MDC_KEY, requestId);
        response.setHeader(RequestIds.HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }
}
