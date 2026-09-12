package com.graduation.backend.auth.config;

import com.graduation.backend.auth.application.wechat.MockWechatClient;
import com.graduation.backend.auth.application.wechat.WechatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信登录客户端的装配点。
 *
 * <p>当前仓库只实现 {@code mock}：本机与测试环境不依赖真实 AppSecret。
 * 选择 {@code app.wechat.mode=wechat} 会直接启动失败，而不是悄悄退回模拟实现 ——
 * 真实接入需要新增 {@code spring-boot-starter-web} 客户端并注入 AppSecret 环境变量。
 */
@Configuration
public class WechatClientConfig {

    @Bean
    public WechatClient wechatClient(@Value("${app.wechat.mode:mock}") String mode,
                                     @Value("${app.wechat.mock-openid:}") String mockOpenid) {
        if (!"mock".equalsIgnoreCase(mode)) {
            throw new IllegalStateException(
                    "app.wechat.mode=" + mode + " 尚未实现：当前版本只提供模拟微信登录，请设置 app.wechat.mode=mock");
        }
        return new MockWechatClient(mockOpenid);
    }
}
