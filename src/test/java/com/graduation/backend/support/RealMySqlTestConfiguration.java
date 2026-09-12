package com.graduation.backend.support;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * 真实 MySQL 上的测试基础设施，替代 {@code TestcontainersConfiguration}（本机无 Docker）。
 *
 * <p>每个测试上下文启动时先 {@code clean()} 再 {@code migrate()}，让 V1～V6 从空 schema 重放。
 * {@code clean()} 会删除目标 schema 内的全部对象，因此执行前必须确认连的是用完即弃的 schema：
 * schema 名必须以 {@value #DISPOSABLE_SUFFIX} 结尾，否则直接抛错、不做任何修改。
 */
@TestConfiguration(proxyBeanMethods = false)
public class RealMySqlTestConfiguration {

    private static final String DISPOSABLE_SUFFIX = "_it";

    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            assertDisposableSchema(flyway.getConfiguration().getDataSource());
            flyway.clean();
            flyway.migrate();
        };
    }

    private void assertDisposableSchema(DataSource dataSource) {
        String catalog;
        try (Connection connection = dataSource.getConnection()) {
            catalog = connection.getCatalog();
        } catch (SQLException failure) {
            throw new IllegalStateException("无法连接测试数据库，请检查 DB_IT_URL / DB_IT_USERNAME / DB_IT_PASSWORD", failure);
        }
        if (catalog == null || !catalog.toLowerCase(Locale.ROOT).endsWith(DISPOSABLE_SUFFIX)) {
            throw new IllegalStateException("拒绝在 schema `" + catalog + "` 上执行 Flyway clean()："
                    + "测试目标 schema 名必须以 " + DISPOSABLE_SUFFIX + " 结尾（例如 graduation_mvp_it），"
                    + "以免误删非测试数据。");
        }
    }
}
