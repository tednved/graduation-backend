package com.graduation.backend.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 需要真实 MySQL 的测试基类。
 *
 * <p>本机没有 Docker，因此不走 Testcontainers：直接连接本机 MySQL 的一次性 schema，
 * 由 {@link RealMySqlTestConfiguration} 在每个上下文启动时 clean + migrate，
 * 让 V1～V6 从空 schema 真实重放，并配合 {@code ddl-auto=validate} 校验实体映射。
 *
 * <p>{@link MockMvc} 手工构造而非用 {@code @AutoConfigureMockMvc}：Boot 4 把该注解放进了
 * 独立的 webmvc 测试模块，本工程未依赖它。{@code webAppContextSetup} 只会装载注册为
 * {@code Filter} Bean 的过滤器，而 Spring Security 的过滤链是通过 Servlet 容器初始化器注册的，
 * 因此必须用 {@link SecurityMockMvcConfigurers#springSecurity()} 显式挂上，
 * 否则请求会绕过安全过滤链直接进入 Controller，受保护接口表现为 401 而非鉴权失败。
 *
 * <p>未提供 {@code -Ddb.it.username} 或环境变量 {@code DB_IT_USERNAME} 时，子类应通过
 * {@code @EnabledIf("databaseConfigured")} 整体跳过，而不是让构建失败。
 *
 * <p>JWT 密钥在运行时随机生成：仓库里不留任何可用密钥，同时满足 HS256 对密钥长度的要求。
 */
@SpringBootTest
@Import(RealMySqlTestConfiguration.class)
public abstract class RealMySqlTestBase {

    private static final String JWT_SECRET = randomSecret();

    @Autowired
    private WebApplicationContext applicationContext;

    protected MockMvc mockMvc;

    /**
     * 是否具备连接真实 MySQL 的凭据。解析顺序与 DB-01 的集成测试一致：{@code -D} 优先于环境变量。
     */
    public static boolean databaseConfigured() {
        String username = System.getProperty("db.it.username");
        if (username == null || username.isBlank()) {
            username = System.getenv("DB_IT_USERNAME");
        }
        return username != null && !username.isBlank();
    }

    @DynamicPropertySource
    static void testOnlyProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> JWT_SECRET);
    }

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
