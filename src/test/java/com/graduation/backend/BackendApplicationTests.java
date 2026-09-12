package com.graduation.backend;

import com.graduation.backend.support.RealMySqlTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 应用能在真实 MySQL 上从空 schema 启动。
 *
 * <p>上下文启动本身就覆盖了验收里最关键的一条：Flyway 在空库上重放 V1～V6 之后，
 * Hibernate 的 {@code ddl-auto=validate} 必须全部通过——实体映射与迁移脚本不一致会在这里失败。
 */
@EnabledIf("databaseConfigured")
class BackendApplicationTests extends RealMySqlTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("上下文启动成功，且 V1～V6 在空 schema 上全部迁移成功")
    void contextLoadsOnEmptyDatabase() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL AND success = 1 "
                        + "ORDER BY installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    @DisplayName("单校区决策：V5 只写入 code=MAIN 的启用校区")
    void seedsOnlyMainCampus() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM campuses", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM campuses WHERE code = 'MAIN' AND status = 'ENABLED'", Integer.class))
                .isEqualTo(1);
    }
}
