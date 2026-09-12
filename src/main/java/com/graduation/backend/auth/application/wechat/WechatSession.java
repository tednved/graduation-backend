package com.graduation.backend.auth.application.wechat;

/**
 * 微信登录会话。
 *
 * <p>{@code sessionKey} 不在此模型中保留：本系统不使用微信加密数据解密能力，
 * 拿到后立即丢弃，既不入库也不返回。
 */
public record WechatSession(String openid, String unionid) {
}
