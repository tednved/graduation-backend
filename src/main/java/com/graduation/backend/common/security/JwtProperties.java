package com.graduation.backend.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT 与刷新令牌的有效期配置。
 *
 * <p>密钥只从 {@code APP_JWT_SECRET} 注入，仓库内不提供任何默认值；长度不足 32 字节直接启动失败，
 * 避免用弱密钥签出可被离线爆破的 Token。Access Token 固定 15 分钟，Refresh Token 固定 30 天（契约 §8.1）。
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl, String issuer) {

    public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    public static final Duration REFRESH_TTL = Duration.ofDays(30);
    public static final long ACCESS_TTL_SECONDS = 900L;
    public static final long REFRESH_TTL_SECONDS = 2592000L;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret 未配置或长度不足 32 字节，请通过 APP_JWT_SECRET 注入");
        }
        if (accessTtl == null) {
            accessTtl = ACCESS_TTL;
        }
        if (refreshTtl == null) {
            refreshTtl = REFRESH_TTL;
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "graduation-backend";
        }
    }
}
