package com.graduation.backend.auth.application.wechat;

/**
 * 用小程序 {@code code} 换取微信会话标识。
 *
 * <p>实现方必须保证：微信侧失败统一抛出 {@code AUTH_INVALID_CODE}，不向调用方暴露微信原始错误码、
 * {@code errmsg}、{@code session_key} 或 AppSecret。
 */
public interface WechatClient {

    WechatSession exchange(String code);
}
