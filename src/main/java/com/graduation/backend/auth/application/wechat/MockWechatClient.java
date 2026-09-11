package com.graduation.backend.auth.application.wechat;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 本地与测试用的模拟微信登录。
 *
 * <p>{@code openid} 由 {@code code} 的 SHA-256 派生，因此同一个 code 总是映射到同一个用户，
 * 便于反复走通登录链路；不同 code 一定得到不同用户。明文 code 不写日志、不落库。
 */
public class MockWechatClient implements WechatClient {

    /** 派生 openid 时截取的摘要长度，保证 openid 不超过 64 字符。 */
    private static final int OPENID_DIGEST_CHARS = 24;
    private static final HexFormat HEX = HexFormat.of();

    @Override
    public WechatSession exchange(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CODE, "微信登录凭证无效，请重新登录");
        }
        return new WechatSession("mock_" + digest(code).substring(0, OPENID_DIGEST_CHARS), null);
    }

    private String digest(String code) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(sha256.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }
}
