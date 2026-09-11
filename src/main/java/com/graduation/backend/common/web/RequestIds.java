package com.graduation.backend.common.web;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 当前请求的 {@code X-Request-Id}。由 {@link RequestIdFilter} 写入 MDC，
 * 响应信封从这里读取，保证响应头与响应体中的 requestId 完全一致。
 */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestIds() {
    }

    /** 返回当前请求的 requestId；不在请求线程中时退回一个临时值，避免信封缺字段。 */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value != null ? value : generate();
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
