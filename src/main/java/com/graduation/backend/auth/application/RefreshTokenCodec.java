package com.graduation.backend.auth.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh Token 的生成与哈希。
 *
 * <p>明文使用 32 字节 {@link SecureRandom} 生成（约 256 位熵），只在签发响应中出现一次；
 * 落库的是 SHA-256 十六进制哈希，因此数据库泄露也无法直接换取令牌。
 */
@Component
public class RefreshTokenCodec {

    private static final int TOKEN_BYTES = 32;
    private static final HexFormat HEX = HexFormat.of();

    private final SecureRandom random = new SecureRandom();

    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String plainToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(plainToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }
}
