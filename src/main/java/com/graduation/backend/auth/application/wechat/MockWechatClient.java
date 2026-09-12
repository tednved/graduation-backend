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
 * <p>本地联调可配置固定 {@code openid}，保证每次 {@code wx.login} 都回到同一账号；
 * 未配置时由 {@code code} 的 SHA-256 派生，供自动化测试创建多个用户。明文 code 不写日志、不落库。
 */
public class MockWechatClient implements WechatClient {

    /** 派生 openid 时截取的摘要长度，保证 openid 不超过 64 字符。 */
    private static final int OPENID_DIGEST_CHARS = 24;
    private static final HexFormat HEX = HexFormat.of();
    private final String fixedOpenid;

    public MockWechatClient() {
        this(null);
    }

    public MockWechatClient(String fixedOpenid) {
        this.fixedOpenid = fixedOpenid;
    }

    @Override
    public WechatSession exchange(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CODE, "微信登录凭证无效，请重新登录");
        }
        String openid = fixedOpenid == null || fixedOpenid.isBlank()
                ? "mock_" + digest(code).substring(0, OPENID_DIGEST_CHARS)
                : fixedOpenid;
        return new WechatSession(openid, null);
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
