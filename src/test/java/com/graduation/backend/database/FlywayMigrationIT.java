package com.graduation.backend.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DB-01 迁移集成测试。
 *
 * <p>必须连接真实 MySQL 8 实例，不使用 H2 / SQLite / mock 替代。目标 schema 为空，
 * Flyway 从 {@code classpath:db/migration} 执行 V1～V5。
 *
 * <p>数据源有两种来源，按以下优先级解析：
 * <ol>
 *   <li>未提供 {@code db.it.url} 时，使用项目已有 Testcontainers 依赖启动 {@code mysql:8.0} 容器
 *       （需要 Docker）。容器每次全新启动，schema 由构造保证为空。两个容器专属回归用例在容器内
 *       另建带随机后缀的临时 schema：建库与授权走容器管理员账号，业务账号只获得该 schema 的权限，
 *       从而复现外部模式下的真实权限边界，而不是依赖业务账号的隐式全局权限。</li>
 *   <li>提供 {@code db.it.url} 时，连接该外部真实 MySQL 8 实例。此路径必须先通过
 *       {@link #assertTargetIsDisposable} 的前置保护，否则不得执行任何迁移。</li>
 * </ol>
 *
 * <p>外部模式的前置保护（在 {@code Flyway.migrate()} 之前执行）要求全部满足：
 * <ul>
 *   <li>显式测试用途确认 {@code -Ddb.it.confirm=}<b>{@value #EXTERNAL_CONFIRMATION}</b>；
 *       缺少或值不符即拒绝，且此时尚未建立任何连接；</li>
 *   <li>显式用户名 {@code -Ddb.it.username} 或环境变量 {@code DB_IT_USERNAME}，不默认为 {@code root}；</li>
 *   <li>JDBC URL 指定了 schema（连接后 {@code Connection.getCatalog()} 非空）；</li>
 *   <li>该 schema 内没有任何用户对象（表、视图、存储程序、触发器）。</li>
 * </ul>
 * 任一条件不满足即抛出异常，<b>零迁移、零业务写入</b>；本测试不调用 {@code Flyway.clean()}，
 * 也不执行任何 {@code DROP DATABASE}／{@code DROP TABLE}。密码通过 {@code -Ddb.it.password}
 * 或环境变量 {@code DB_IT_PASSWORD} 输入，不作为默认值写死在代码里。
 *
 * <p>运行命令（二选一）：
 * <pre>
 * .\mvnw.cmd -Dtest=FlywayMigrationIT test
 *
 * .\mvnw.cmd -Dtest=FlywayMigrationIT ^
 *     "-Ddb.it.url=jdbc:mysql://127.0.0.1:3306/&lt;专用空 schema&gt;" ^
 *     "-Ddb.it.username=&lt;user&gt;" "-Ddb.it.confirm=DB-01-FlywayMigrationIT" test
 * </pre>
 * 外部模式另可用环境变量 {@code DB_IT_USERNAME} / {@code DB_IT_PASSWORD} 传入凭据，避免出现在命令行。
 * 注意 {@code mvnw.cmd} 是 Windows 批处理，URL 中若含 {@code &} 会被 {@code cmd.exe} 拆分，
 * 因此外部模式的 URL 不应携带查询参数。
 */
class FlywayMigrationIT {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";

    /** 外部模式必须原样提供的测试用途确认值，用于阻止误连已有库或生产库。 */
    static final String EXTERNAL_CONFIRMATION = "DB-01-FlywayMigrationIT";

    private static final String URL_PROPERTY = "db.it.url";
    private static final String USERNAME_PROPERTY = "db.it.username";
    private static final String PASSWORD_PROPERTY = "db.it.password";
    private static final String CONFIRM_PROPERTY = "db.it.confirm";
    private static final String USERNAME_ENV = "DB_IT_USERNAME";
    private static final String PASSWORD_ENV = "DB_IT_PASSWORD";

    private static final Set<String> BUSINESS_TABLES = Set.of(
            "campuses", "users", "refresh_tokens",
            "file_objects", "certifications", "categories", "items", "item_images",
            "favorites", "orders", "order_snapshots", "order_events",
            "reviews", "notifications", "audit_logs");

    /** 容器默认 schema，由镜像入口脚本创建并把该 schema 的全部权限授予 {@link #CONTAINER_USERNAME}。 */
    private static final String CONTAINER_DATABASE = "db01_it";
    private static final String CONTAINER_USERNAME = "db01_it_user";
    private static final String CONTAINER_PASSWORD = "db01_it_password";

    /**
     * 临时 schema 的管理员账号。{@code MySQLContainer} 的 {@code configure()} 在 {@code start()}
     * 时才执行，并把 {@code MYSQL_ROOT_PASSWORD} 覆盖为 {@code withPassword} 的取值，因此 root 口令
     * 恒等于 {@code container.getPassword()}，既不写死也不能通过 {@code withEnv} 另行指定。
     * root 只用于容器内的临时 schema 建库与授权，不参与任何业务迁移。
     */
    private static final String CONTAINER_ADMIN_USERNAME = "root";

    private static MySQLContainer container;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    private static MigrateResult firstMigration;

    @BeforeAll
    static void migrateEmptyDatabase() {
        String externalUrl = System.getProperty(URL_PROPERTY);
        if (externalUrl == null || externalUrl.isBlank()) {
            container = new MySQLContainer("mysql:8.0")
                    .withDatabaseName(CONTAINER_DATABASE)
                    .withUsername(CONTAINER_USERNAME)
                    .withPassword(CONTAINER_PASSWORD)
                    .withUrlParam("allowPublicKeyRetrieval", "true")
                    .withUrlParam("useSSL", "false");
            container.start();
            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            jdbcUrl = externalUrl;
            username = requiredSetting(USERNAME_PROPERTY, USERNAME_ENV);
            password = optionalSetting(PASSWORD_PROPERTY, PASSWORD_ENV);
            assertTargetIsDisposable(externalUrl, username, password, System.getProperty(CONFIRM_PROPERTY));
        }
        firstMigration = flyway().migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
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

    @Test
    @DisplayName("外部模式保护: 缺少测试用途确认参数即拒绝，且不建立任何连接")
    void rejectsExternalModeWithoutConfirmation() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> assertTargetIsDisposable("jdbc:mysql://127.0.0.1:3306/whatever", "someone", "",
                        null));
        assertTrue(failure.getMessage().contains(CONFIRM_PROPERTY),
                "拒绝原因应指明缺少 " + CONFIRM_PROPERTY + "，实际: " + failure.getMessage());
    }

    @Test
    @DisplayName("外部模式保护: 确认值不符或缺少显式用户名即拒绝")
    void rejectsExternalModeWithWrongConfirmationOrMissingUsername() {
        assertThrows(IllegalStateException.class,
                () -> assertTargetIsDisposable("jdbc:mysql://127.0.0.1:3306/whatever", "someone", "",
                        "not-the-agreed-token"));
        assertThrows(IllegalStateException.class,
                () -> assertTargetIsDisposable("jdbc:mysql://127.0.0.1:3306/whatever", "  ", "",
                        EXTERNAL_CONFIRMATION));
    }

    @Test
    @DisplayName("外部模式回归(容器): 空 schema 通过前置保护")
    void acceptsEmptySchema() throws SQLException {
        assumeTrue(container != null, "仅容器模式执行：外部模式的目标空 schema 由调用方提供，不在此断言");
        String schema = createDisposableSchema("it_empty");
        assertDoesNotThrow(() -> assertTargetIsDisposable(
                containerUrl(schema), container.getUsername(), container.getPassword(), EXTERNAL_CONFIRMATION));
    }

    @Test
    @DisplayName("外部模式回归(容器): 已有哨兵表的 schema 被拒绝，哨兵与迁移历史保持不变")
    void rejectsSchemaWithSentinelTableAndLeavesItUnchanged() throws SQLException {
        assumeTrue(container != null, "仅容器模式执行：哨兵库只建在容器内，不触碰本机业务库");
        String schema = createDisposableSchema("it_sentinel");
        try (Connection connection = containerConnection(schema);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE `sentinel_marker` (`id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, "
                    + "PRIMARY KEY (`id`)) ENGINE = InnoDB");
        }

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> assertTargetIsDisposable(
                        containerUrl(schema), container.getUsername(), container.getPassword(), EXTERNAL_CONFIRMATION));
        assertTrue(failure.getMessage().contains("sentinel_marker"),
                "拒绝原因应列出已存在的对象，实际: " + failure.getMessage());

        try (Connection connection = containerConnection(schema)) {
            assertEquals(List.of("sentinel_marker"), baseTables(connection),
                    "哨兵表必须原样保留，且不得被迁移创建任何业务表");
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = '" + FLYWAY_HISTORY_TABLE + "'"),
                    "被拒绝的 schema 不得建立 Flyway 迁移历史");
        }
    }

    /**
     * 外部 MySQL 模式的前置保护，必须在任何 {@code migrate()} 之前调用。
     *
     * <p>全部校验通过时正常返回；任一不满足即抛出 {@link IllegalStateException}，调用方不得继续迁移。
     * 本方法只读，不执行 DDL/DML，也不调用 {@code clean()} 或任何 {@code DROP} 语句。
     *
     * @param url          外部实例 JDBC URL，必须已指定 schema
     * @param user         显式用户名，不得为空或默认 root
     * @param secret       密码，可为空字符串
     * @param confirmation 测试用途确认值，必须等于 {@link #EXTERNAL_CONFIRMATION}
     */
    static void assertTargetIsDisposable(String url, String user, String secret, String confirmation) {
        if (confirmation == null || !EXTERNAL_CONFIRMATION.equals(confirmation.trim())) {
            throw new IllegalStateException("外部 MySQL 模式要求显式确认目标为专用空 schema：请传入 -D"
                    + CONFIRM_PROPERTY + "=" + EXTERNAL_CONFIRMATION);
        }
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("外部 MySQL 模式要求显式用户名，不接受默认值；请传入 -D"
                    + USERNAME_PROPERTY + " 或环境变量 " + USERNAME_ENV);
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("外部 MySQL 模式要求提供 JDBC URL");
        }

        try (Connection connection = DriverManager.getConnection(url, user, secret)) {
            String schema = connection.getCatalog();
            if (schema == null || schema.isBlank()) {
                throw new IllegalStateException("外部 MySQL 模式的 JDBC URL 必须指定 schema，当前未指定任何 schema");
            }
            List<String> existingObjects = userObjects(connection);
            if (!existingObjects.isEmpty()) {
                throw new IllegalStateException("目标 schema `" + schema + "` 非空，已存在 "
                        + existingObjects.size() + " 个用户对象 " + existingObjects
                        + "；拒绝迁移。请改用一个专用的空 schema。");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("外部 MySQL 模式无法连接或无法检查目标 schema："
                    + failure.getMessage(), failure);
        }
    }

    private static List<String> userObjects(Connection connection) throws SQLException {
        List<String> objects = new ArrayList<>();
        objects.addAll(query(connection, "SELECT CONCAT('table:', table_name) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_type IN ('BASE TABLE', 'VIEW')"));
        objects.addAll(query(connection, "SELECT CONCAT('routine:', routine_name) FROM information_schema.routines "
                + "WHERE routine_schema = DATABASE()"));
        objects.addAll(query(connection, "SELECT CONCAT('trigger:', trigger_name) FROM information_schema.triggers "
                + "WHERE trigger_schema = DATABASE()"));
        return objects;
    }

    private static List<String> baseTables(Connection connection) throws SQLException {
        return query(connection, "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name");
    }

    private static List<String> query(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    /**
     * 在容器内建立一个独立的空 schema，仅用于回归用例；外部模式不会调用本方法。
     *
     * <p>建库必须使用容器管理员账号：镜像只为业务账号授予默认 schema 的权限，业务账号执行
     * {@code CREATE DATABASE} 会被拒绝。建库后把新 schema 的权限授予业务账号，使后续
     * {@link #containerConnection} 与 {@link #assertTargetIsDisposable} 都以业务账号的受限身份访问，
     * 与外部模式下的实际权限情形保持一致。
     */
    private static String createDisposableSchema(String prefix) throws SQLException {
        String schema = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection connection = DriverManager.getConnection(
                container.getJdbcUrl(), CONTAINER_ADMIN_USERNAME, container.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` "
                    + "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            statement.execute("GRANT ALL PRIVILEGES ON `" + schema + "`.* TO '"
                    + CONTAINER_USERNAME + "'@'%'");
        }
        return schema;
    }

    private static String containerUrl(String schema) {
        return "jdbc:mysql://" + container.getHost() + ":" + container.getMappedPort(3306) + "/" + schema
                + "?allowPublicKeyRetrieval=true&useSSL=false";
    }

    private static Connection containerConnection(String schema) throws SQLException {
        return DriverManager.getConnection(containerUrl(schema), container.getUsername(), container.getPassword());
    }

    private static String requiredSetting(String property, String environmentVariable) {
        String value = optionalSetting(property, environmentVariable);
        if (value.isBlank()) {
            throw new IllegalStateException("外部 MySQL 模式要求显式提供 " + property
                    + "（或环境变量 " + environmentVariable + "）");
        }
        return value;
    }

    private static String optionalSetting(String property, String environmentVariable) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return value == null ? "" : value;
    }

    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations(MIGRATION_LOCATION)
                .load();
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next(), "计数查询未返回结果: " + sql);
            return resultSet.getLong(1);
        }
    }

    /**
     * 插入测试用户。前置数据只存在于测试库中，且调用方必须回滚。
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
