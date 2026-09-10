package com.graduation.backend.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB-01 迁移集成测试。
 *
 * <p>必须连接真实 MySQL 8 实例：使用项目已有 Testcontainers 依赖启动 MySQL 容器，
 * 不使用 H2 / SQLite / mock 替代。容器内数据库为空，Flyway 从 {@code classpath:db/migration}
 * 执行 V1～V5。
 *
 * <p>运行命令：{@code .\mvnw.cmd -Dtest=FlywayMigrationIT test}（需要可用的 Docker 环境）。
 */
@Testcontainers
class FlywayMigrationIT {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";

    private static final Set<String> BUSINESS_TABLES = Set.of(
            "campuses", "users", "refresh_tokens",
            "file_objects", "certifications", "categories", "items", "item_images",
            "favorites", "orders", "order_snapshots", "order_events",
            "reviews", "notifications", "audit_logs");

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0");

    private static MigrateResult firstMigration;

    @BeforeAll
    static void migrateEmptyDatabase() {
        firstMigration = flyway().migrate();
    }

    @Test
    @DisplayName("空数据库执行 classpath:db/migration 返回 5 个成功迁移")
    void executesFiveMigrationsOnEmptyDatabase() {
        assertTrue(firstMigration.success, "Flyway 迁移未成功");
        assertEquals(5, firstMigration.migrationsExecuted, "应执行 V1～V5 共 5 个迁移");
    }

    @Test
    @DisplayName("当前 schema 恰好存在 15 张业务表")
    void createsExactlyFifteenBusinessTables() throws SQLException {
        Set<String> actualTables = new LinkedHashSet<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT table_name FROM information_schema.tables "
                             + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                actualTables.add(resultSet.getString(1).toLowerCase(Locale.ROOT));
            }
        }

        assertTrue(actualTables.remove(FLYWAY_HISTORY_TABLE),
                "缺少 Flyway 迁移历史表 " + FLYWAY_HISTORY_TABLE);
        assertEquals(15, actualTables.size(), "业务表数量应为 15，实际为 " + actualTables);
        assertEquals(BUSINESS_TABLES, actualTables, "业务表集合与 DB-01 规格不一致");
    }

    @Test
    @DisplayName("flyway_schema_history 记录 V1～V5 且全部成功、顺序正确")
    void recordsVersionsOneToFiveInOrder() throws SQLException {
        List<String> appliedVersions = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT version, success FROM flyway_schema_history "
                             + "WHERE version IS NOT NULL ORDER BY installed_rank");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String version = resultSet.getString("version");
                assertTrue(resultSet.getBoolean("success"), "迁移 V" + version + " 未成功");
                appliedVersions.add(version);
            }
        }
        assertEquals(List.of("1", "2", "3", "4", "5"), appliedVersions, "迁移版本与顺序不正确");
    }

    @Test
    @DisplayName("V5 只写入 MAIN 校区与 5 个一级、7 个二级分类")
    void seedsDefaultCampusAndTwoLevelCategories() throws SQLException {
        try (Connection connection = openConnection()) {
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM campuses"), "校区种子数量错误");
            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM campuses WHERE code = 'MAIN' AND name = '默认校区' AND status = 'ENABLED'"),
                    "缺少 MAIN 校区种子");
            assertEquals(5, count(connection, "SELECT COUNT(*) FROM categories WHERE parent_id IS NULL"),
                    "一级分类种子数量错误");
            assertEquals(7, count(connection, "SELECT COUNT(*) FROM categories WHERE parent_id IS NOT NULL"),
                    "二级分类种子数量错误");
            assertEquals(12, count(connection, "SELECT COUNT(*) FROM categories"), "分类种子总数错误");
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM users"), "V5 不得写入用户数据");
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM items"), "V5 不得写入商品数据");
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM orders"), "V5 不得写入订单数据");
        }
    }

    @Test
    @DisplayName("第二次 migrate() 无待执行迁移且校验通过")
    void secondMigrationIsANoOp() {
        Flyway flyway = flyway();
        MigrateResult secondMigration = flyway.migrate();

        assertEquals(0, secondMigration.migrationsExecuted, "重复迁移不应再执行任何版本");
        assertEquals(0, flyway.info().pending().length, "重复迁移后不应存在待执行版本");
        flyway.validate();
    }

    @Test
    @DisplayName("约束: 重复 openid 被唯一键拒绝")
    void rejectsDuplicateOpenid() throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                insertUser(connection, "it-dup-openid", "重复标识用户");
                SQLException failure = assertThrows(SQLException.class,
                        () -> insertUser(connection, "it-dup-openid", "重复标识用户二"));
                assertEquals(1062, failure.getErrorCode(), "重复 openid 应返回唯一键冲突错误码 1062");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    @DisplayName("约束: 同一用户重复收藏同一商品被唯一键拒绝")
    void rejectsDuplicateFavorite() throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                long userId = insertUser(connection, "it-favorite-user", "收藏测试用户");
                long itemId = insertItem(connection, userId);
                insertFavorite(connection, userId, itemId);

                SQLException failure = assertThrows(SQLException.class,
                        () -> insertFavorite(connection, userId, itemId));
                assertEquals(1062, failure.getErrorCode(), "重复收藏应返回唯一键冲突错误码 1062");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    @DisplayName("约束: 买卖双方相同的订单被 CHECK 约束拒绝")
    void rejectsOrderWhereBuyerIsSeller() throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                long userId = insertUser(connection, "it-self-purchase", "自购测试用户");
                long itemId = insertItem(connection, userId);

                SQLException failure = assertThrows(SQLException.class,
                        () -> insertOrder(connection, itemId, userId, userId));

                String message = failure.getMessage() == null
                        ? "" : failure.getMessage().toLowerCase(Locale.ROOT);
                assertTrue(message.contains("chk_orders_buyer_not_seller"),
                        "应触发 chk_orders_buyer_not_seller，实际错误: " + failure.getMessage());
            } finally {
                connection.rollback();
            }
        }
    }

    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(MIGRATION_LOCATION)
                .load();
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next(), "计数查询未返回结果: " + sql);
            return resultSet.getLong(1);
        }
    }

    /**
     * 插入测试用户。前置数据只存在于测试容器中，且调用方必须回滚。
     *
     * @return 新用户主键
     */
    private static long insertUser(Connection connection, String openid, String nickname) throws SQLException {
        String sql = "INSERT INTO `users` (`openid`, `unionid`, `role`, `status`, `nickname`, `campus_id`, "
                + "`certification_status`, `average_rating`, `review_count`, `version`, `created_at`, `updated_at`) "
                + "VALUES (?, NULL, 'USER', 'ACTIVE', ?, 1, 'APPROVED', 0.00, 0, 0, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, openid);
            statement.setString(2, nickname);
            setNow(statement, 3, 4);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                assertTrue(generatedKeys.next(), "插入用户后未返回主键");
                return generatedKeys.getLong(1);
            }
        }
    }

    /**
     * 插入测试商品，分类与校区使用 V5 种子数据。调用方必须回滚。
     *
     * @return 新商品主键
     */
    private static long insertItem(Connection connection, long sellerId) throws SQLException {
        String sql = "INSERT INTO `items` (`seller_id`, `category_id`, `campus_id`, `title`, `description`, `price`, "
                + "`original_price`, `condition_level`, `status`, `admin_lock`, `off_shelf_reason`, `view_count`, "
                + "`favorite_count`, `version`, `published_at`, `created_at`, `updated_at`) "
                + "VALUES (?, 101, 1, '集成测试商品', 'DB-01 集成测试使用的商品描述文本', 10.00, NULL, 'GOOD', 'ON_SALE', "
                + "0, NULL, 0, 0, 0, NULL, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, sellerId);
            setNow(statement, 2, 3);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                assertTrue(generatedKeys.next(), "插入商品后未返回主键");
                return generatedKeys.getLong(1);
            }
        }
    }

    private static void insertFavorite(Connection connection, long userId, long itemId) throws SQLException {
        String sql = "INSERT INTO `favorites` (`user_id`, `item_id`, `created_at`) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, itemId);
            statement.setTimestamp(3, now());
            statement.executeUpdate();
        }
    }

    private static void insertOrder(Connection connection, long itemId, long buyerId, long sellerId)
            throws SQLException {
        String sql = "INSERT INTO `orders` (`order_no`, `item_id`, `buyer_id`, `seller_id`, `client_request_id`, "
                + "`amount`, `trade_mode`, `status`, `version`, `created_at`, `updated_at`) "
                + "VALUES (?, ?, ?, ?, ?, 10.00, 'OFFLINE', 'PENDING_CONFIRMATION', 0, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "IT-" + UUID.randomUUID());
            statement.setLong(2, itemId);
            statement.setLong(3, buyerId);
            statement.setLong(4, sellerId);
            statement.setString(5, "IT-REQUEST-" + UUID.randomUUID());
            setNow(statement, 6, 7);
            statement.executeUpdate();
        }
    }

    private static void setNow(PreparedStatement statement, int firstIndex, int secondIndex) throws SQLException {
        Timestamp currentTimestamp = now();
        statement.setTimestamp(firstIndex, currentTimestamp);
        statement.setTimestamp(secondIndex, currentTimestamp);
    }

    private static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }
}
