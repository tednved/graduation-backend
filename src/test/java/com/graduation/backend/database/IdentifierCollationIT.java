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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB-02 标识符精确比较集成测试。
 *
 * <p>必须连接真实 MySQL 8.0.17+ 实例（{@code utf8mb4_0900_bin} 自 8.0.17 起提供），
 * 不使用 H2 / SQLite / mock 替代。本测试验证 V6 迁移的两个目的：
 * <ol>
 *   <li>V6 只把 6 个不透明标识符列（{@code users.openid}、{@code users.unionid}、
 *       {@code refresh_tokens.device_id}、{@code file_objects.object_key}、
 *       {@code orders.order_no}、{@code orders.client_request_id}）改成逐字符比较的
 *       {@code utf8mb4_0900_bin}，其余展示与搜索文本仍用表默认 {@code utf8mb4_0900_ai_ci}；</li>
 *   <li>两条迁移路径都不丢数据、索引、外键与表：空库直接 V1～V6；以及先 {@code target("5")}
 *       停在 V5、写入样例数据、再升到 V6。</li>
 * </ol>
 *
 * <p><b>测试库的来源与安全边界</b>：本测试只在自己建立的临时 schema 中读写，schema 名一律为
 * {@code db02_it_*} 加随机后缀；对调用方提供的外部 URL，只把它当作连接锚点（并允许在其所属实例上
 * {@code CREATE DATABASE}），<b>不在该 URL 指定的 schema 内执行任何 DDL 或 DML</b>，也不调用
 * {@code Flyway.clean()}。测试不删除自己建立的临时 schema——清理命令见 DB-02 交接报告。
 *
 * <p>数据源解析优先级与 DB-01 的 {@link FlywayMigrationIT} 一致：未提供 {@code db.it.url} 时启动
 * {@code mysql:8.0} 容器（需要 Docker）；提供该属性时连接外部真实 MySQL 8，并先通过
 * {@link #assertSafeExternalTarget} 的前置保护。确认值接受 {@value #EXTERNAL_CONFIRMATION}，
 * 也接受 {@link FlywayMigrationIT#EXTERNAL_CONFIRMATION}，以便两类用例在同一次运行中共用锚点。
 * 密码通过 {@code -Ddb.it.password} 或环境变量 {@code DB_IT_PASSWORD} 输入，不写死默认值。
 *
 * <p>运行命令（二选一）：
 * <pre>
 * .\mvnw.cmd -Dtest=FlywayMigrationIT,IdentifierCollationIT test
 *
 * .\mvnw.cmd -Dtest=FlywayMigrationIT,IdentifierCollationIT ^
 *     "-Ddb.it.url=jdbc:mysql://127.0.0.1:3306/&lt;专用空 schema&gt;" ^
 *     "-Ddb.it.username=&lt;user&gt;" "-Ddb.it.confirm=DB-01-FlywayMigrationIT" test
 * </pre>
 * 外部模式的 URL 不应携带查询参数：{@code mvnw.cmd} 是 Windows 批处理，URL 中的 {@code &}
 * 会被 {@code cmd.exe} 拆分。
 */
class IdentifierCollationIT {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";

    /** 外部模式必须原样提供的测试用途确认值。 */
    static final String EXTERNAL_CONFIRMATION = "DB-02-IdentifierCollationIT";

    private static final String URL_PROPERTY = "db.it.url";
    private static final String USERNAME_PROPERTY = "db.it.username";
    private static final String PASSWORD_PROPERTY = "db.it.password";
    private static final String CONFIRM_PROPERTY = "db.it.confirm";
    private static final String USERNAME_ENV = "DB_IT_USERNAME";
    private static final String PASSWORD_ENV = "DB_IT_PASSWORD";

    /** V6 之后必须逐字符比较的 6 个列，形如 {@code 表.列}。 */
    private static final List<String> EXACT_COLUMNS = List.of(
            "users.openid", "users.unionid", "refresh_tokens.device_id", "file_objects.object_key",
            "orders.order_no", "orders.client_request_id");

    /** 受 V6 影响的 4 张表，用于比对列定义与索引。 */
    private static final List<String> TOUCHED_TABLES = List.of("users", "refresh_tokens", "file_objects", "orders");

    private static final String EXACT_COLLATION = "utf8mb4_0900_bin";
    private static final String TABLE_DEFAULT_COLLATION = "utf8mb4_0900_ai_ci";

    /** DB-01 建立的 15 张业务表，V6 不得增减。 */
    private static final List<String> BUSINESS_TABLES = List.of(
            "audit_logs", "campuses", "categories", "certifications", "favorites", "file_objects",
            "item_images", "items", "notifications", "order_events", "order_snapshots", "orders",
            "refresh_tokens", "reviews", "users");

    private static final String CONTAINER_DATABASE = "db02_it_anchor";
    private static final String CONTAINER_USERNAME = "db02_it_user";
    private static final String CONTAINER_PASSWORD = "db02_it_password";
    /** {@code MySQLContainer} 在 {@code start()} 时把 root 口令覆盖为 {@code withPassword} 的取值。 */
    private static final String CONTAINER_ADMIN_USERNAME = "root";

    private static MySQLContainer container;

    /** 连接锚点的实例前缀（形如 {@code jdbc:mysql://host:port/}）与其后的 URL 参数。 */
    private static String instancePrefix;
    private static String instanceParameters;

    /** 业务账号：执行迁移与断言的身份；容器模式下由管理员授权后才拥有临时 schema 权限。 */
    private static String username;
    private static String password;

    /** 建库账号：容器模式为 root，外部模式与业务账号相同。 */
    private static String adminUsername;
    private static String adminPassword;

    /** 空库路径的 schema，{@code @BeforeAll} 中直接迁到最新版本。 */
    private static String freshSchema;
    private static MigrateResult freshMigration;

    @BeforeAll
    static void prepareSchemas() throws SQLException {
        resolveDataSource();
        freshSchema = createDisposableSchema("db02_it_fresh");
        freshMigration = flywayFor(freshSchema).migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    // ------------------------------------------------------------------
    // 路径一：空库直接 V1～V6
    // ------------------------------------------------------------------

    @Test
    @DisplayName("空库执行 V1～V6 共 6 个迁移并保留 15 张业务表")
    void freshDatabaseMigratesThroughV6AndKeepsFifteenTables() throws SQLException {
        assertTrue(freshMigration.success, "Flyway 迁移未成功");
        assertEquals(6, freshMigration.migrationsExecuted, "空库应执行 V1～V6 共 6 个迁移");

        try (Connection connection = openConnection(freshSchema)) {
            assertEquals(BUSINESS_TABLES, baseTables(connection), "业务表集合与 DB-01 基线不一致");
            assertEquals(List.of("1", "2", "3", "4", "5", "6"), appliedVersions(connection),
                    "迁移版本与顺序不正确");
        }
    }

    @Test
    @DisplayName("仅 6 个标识符列使用 utf8mb4_0900_bin，其余文本列保持表默认排序规则")
    void onlyExactIdentifierColumnsUseBinaryCollation() throws SQLException {
        try (Connection connection = openConnection(freshSchema)) {
            Map<String, String> actual = columnCollations(connection);
            for (String qualified : EXACT_COLUMNS) {
                assertEquals(EXACT_COLLATION, actual.get(qualified), qualified + " 必须逐字符区分大小写");
            }

            List<String> unexpected = new ArrayList<>();
            actual.forEach((qualified, collation) -> {
                if (!EXACT_COLUMNS.contains(qualified) && !TABLE_DEFAULT_COLLATION.equals(collation)) {
                    unexpected.add(qualified + "=" + collation);
                }
            });
            assertEquals(List.of(), unexpected, "V6 不得改动其他文本列的排序规则，也不得做整表转换");
        }
    }

    @Test
    @DisplayName("openid 区分大小写：大小写不同可共存，完全相同仍被唯一键拒绝")
    void openidIsCaseSensitiveAndStillUnique() throws SQLException {
        try (Connection connection = openConnection(freshSchema)) {
            connection.setAutoCommit(false);
            try {
                long lower = insertUser(connection, "db02-openid-alpha", "小写标识用户");
                long upper = insertUser(connection, "DB02-OPENID-ALPHA", "大写标识用户");
                assertTrue(lower != upper, "大小写不同的 openid 必须是两个不同用户");

                assertEquals(Map.of("db02-openid-alpha", lower, "DB02-OPENID-ALPHA", upper),
                        userIdsByOpenid(connection, "db02-openid-alpha", "DB02-OPENID-ALPHA"),
                        "按精确值必须查回各自的用户，且读出值逐字符不变");

                SQLException duplicate = assertThrows(SQLException.class,
                        () -> insertUser(connection, "db02-openid-alpha", "重复标识用户"));
                assertEquals(1062, duplicate.getErrorCode(), "完全相同的 openid 仍应被唯一键拒绝");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    @DisplayName("object_key、device_id、order_no 的查询与唯一性都不折叠大小写")
    void objectKeyDeviceIdAndOrderNoAreCaseSensitive() throws SQLException {
        try (Connection connection = openConnection(freshSchema)) {
            connection.setAutoCommit(false);
            try {
                long owner = insertUser(connection, "db02-key-owner", "键测试用户");

                insertFileObject(connection, owner, "db02/Alpha/Key.PNG");
                insertFileObject(connection, owner, "db02/alpha/key.png");
                SQLException duplicateKey = assertThrows(SQLException.class,
                        () -> insertFileObject(connection, owner, "db02/Alpha/Key.PNG"));
                assertEquals(1062, duplicateKey.getErrorCode(), "完全相同的 object_key 应被唯一键拒绝");
                assertEquals("db02/Alpha/Key.PNG", storedObjectKey(connection, owner, "db02/Alpha/Key.PNG"),
                        "读回值必须逐字符相同，不得 trim 或 lowercase");
                assertEquals(1, rowsWith(connection,
                        "SELECT object_key FROM file_objects", "object_key", "db02/alpha/key.png"),
                        "object_key 等值查询不得折叠大小写");

                insertRefreshToken(connection, owner, "db02-device-ONE");
                insertRefreshToken(connection, owner, "db02-device-one");
                assertEquals(1, rowsWith(connection,
                        "SELECT device_id FROM refresh_tokens", "device_id", "db02-device-ONE"),
                        "device_id 等值查询不得折叠大小写");
                assertEquals(2, count(connection,
                        "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = " + owner),
                        "大小写不同的 device_id 必须可共存");

                long seller = insertUser(connection, "db02-no-seller", "单号卖家");
                long itemId = insertItem(connection, seller);
                insertOrder(connection, itemId, owner, seller, "db02-ORDER-One", "db02-request-One");
                insertOrder(connection, itemId, owner, seller, "db02-order-one", "db02-REQUEST-one");
                SQLException duplicateNo = assertThrows(SQLException.class,
                        () -> insertOrder(connection, itemId, owner, seller, "db02-ORDER-One",
                                "db02-request-One"));
                assertEquals(1062, duplicateNo.getErrorCode(), "完全相同的 order_no 应被唯一键拒绝");
                assertEquals(1, rowsWith(connection,
                        "SELECT order_no FROM orders", "order_no", "db02-order-one"),
                        "order_no 等值查询不得折叠大小写");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    @DisplayName("同一买家的 client_request_id 区分大小写，完全相同仍被复合唯一键拒绝")
    void clientRequestIdIsCaseSensitivePerBuyer() throws SQLException {
        try (Connection connection = openConnection(freshSchema)) {
            connection.setAutoCommit(false);
            try {
                long seller = insertUser(connection, "db02-idem-seller", "幂等卖家");
                long buyer = insertUser(connection, "db02-idem-buyer", "幂等买家");
                long otherBuyer = insertUser(connection, "db02-idem-buyer2", "幂等买家二");
                long itemId = insertItem(connection, seller);

                insertOrder(connection, itemId, buyer, seller, "db02-idem-A", "Pay-Request-01");
                insertOrder(connection, itemId, buyer, seller, "db02-idem-B", "pay-request-01");

                SQLException duplicate = assertThrows(SQLException.class,
                        () -> insertOrder(connection, itemId, buyer, seller, "db02-idem-C", "Pay-Request-01"));
                assertEquals(1062, duplicate.getErrorCode(),
                        "同一买家完全相同的 client_request_id 应被 uk_orders_buyer_request 拒绝");

                assertDoesNotThrow(() -> insertOrder(connection, itemId, otherBuyer, seller,
                                "db02-idem-D", "Pay-Request-01"),
                        "不同买家的相同 client_request_id 必须仍可各自下单");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    @DisplayName("尾随空格参与比较，且写读往返不 trim、不改写大小写")
    void trailingSpacesAreSignificantAndValuesAreNotNormalized() throws SQLException {
        try (Connection connection = openConnection(freshSchema)) {
            connection.setAutoCommit(false);
            try {
                insertUser(connection, "db02-pad-user", "空格用户");
                insertUser(connection, "db02-pad-user ", "带尾随空格用户");

                assertEquals(1, rowsWith(connection, "SELECT openid FROM users", "openid", "db02-pad-user"),
                        "尾随空格必须参与比较：'db02-pad-user' 只应命中自身");
                assertEquals(1, rowsWith(connection, "SELECT openid FROM users", "openid", "db02-pad-user "),
                        "尾随空格必须参与比较：带空格的 openid 只应命中自身");
                assertTrue(storedOpenid(connection, "db02-pad-user ").length() == "db02-pad-user ".length(),
                        "写读往返不得 trim 尾随空格");

                insertUser(connection, "DB02-MiXeD-Case ", "混合大小写用户");
                assertEquals("DB02-MiXeD-Case ", storedOpenid(connection, "DB02-MiXeD-Case "),
                        "写读往返必须逐字符一致，不得 trim 或 lowercase");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    @DisplayName("展示文本沿用表默认排序规则，仍然大小写不敏感")
    void displayTextKeepsTableDefaultCaseInsensitiveCollation() throws SQLException {
        try (Connection connection = openConnection(freshSchema)) {
            connection.setAutoCommit(false);
            try {
                insertUser(connection, "db02-display-text", "Db02DisplayName");
                assertEquals(1, rowsWith(connection, "SELECT nickname FROM users", "nickname",
                                "DB02DISPLAYNAME"),
                        "nickname 属展示文本，必须保持表默认 ai_ci 的大小写不敏感比较");
            } finally {
                connection.rollback();
            }
        }
    }

    // ------------------------------------------------------------------
    // 路径二：先停在 V5、写入样例数据、再升到 V6
    // ------------------------------------------------------------------

    @Test
    @DisplayName("target V5 基线不变；升到 V6 后数据、列定义、索引、外键、表与 V1～V5 校验和全部保留")
    void v5ToV6UpgradePreservesDataDefinitionAndChecksums() throws SQLException {
        String upgradeSchema = createDisposableSchema("db02_it_upgrade");
        Flyway toV5 = Flyway.configure()
                .dataSource(schemaUrl(upgradeSchema), username, password)
                .locations(MIGRATION_LOCATION)
                .target("5")
                .load();
        MigrateResult baseline = toV5.migrate();
        assertTrue(baseline.success, "target V5 迁移未成功");
        assertEquals(5, baseline.migrationsExecuted, "target V5 应只执行 V1～V5");

        Map<String, String> definitionsBefore;
        Map<String, String> indexesBefore;
        List<String> foreignKeysBefore;
        Map<String, Integer> checksumsBefore;
        Map<String, Integer> rowCountsBefore;
        long userId;
        long itemId;

        try (Connection connection = openConnection(upgradeSchema)) {
            // DB-01 的 V1～V5 基线用例，在 V5 隔离态下重新核对。
            assertEquals(BUSINESS_TABLES, baseTables(connection), "V5 基线的业务表集合不正确");
            assertEquals(List.of("1", "2", "3", "4", "5"), appliedVersions(connection),
                    "V5 基线的迁移版本不正确");
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM campuses WHERE code = 'MAIN'"),
                    "V5 种子校区缺失");
            assertEquals(12, count(connection, "SELECT COUNT(*) FROM categories"), "V5 种子分类缺失");
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM users"), "V5 不得写入用户数据");

            Map<String, String> collationsAtV5 = columnCollations(connection);
            for (String qualified : EXACT_COLUMNS) {
                assertEquals(TABLE_DEFAULT_COLLATION, collationsAtV5.get(qualified),
                        qualified + " 在 V6 之前应仍继承表默认 ai_ci——缺口来自旧规划，不是 V6 之外的改动");
            }

            connection.setAutoCommit(false);
            try {
                userId = insertUser(connection, "Db02Upgrade-Openid", "升级路径用户");
                long sellerId = insertUser(connection, "db02upgrade-seller", "升级路径卖家");
                itemId = insertItem(connection, sellerId);
                insertFileObject(connection, userId, "db02/Upgrade/Key.BIN");
                insertRefreshToken(connection, userId, "Db02Upgrade-Device");
                insertOrder(connection, itemId, userId, sellerId, "Db02Upgrade-No", "Db02Upgrade-Req");
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }

            definitionsBefore = columnDefinitions(connection, TOUCHED_TABLES);
            indexesBefore = indexesOf(connection, TOUCHED_TABLES);
            foreignKeysBefore = foreignKeys(connection);
            checksumsBefore = migrationChecksums(connection);
            rowCountsBefore = rowCounts(connection);
            assertEquals(27, foreignKeysBefore.size(), "V5 基线的外键数量应为 27");
        }

        Flyway toLatest = Flyway.configure()
                .dataSource(schemaUrl(upgradeSchema), username, password)
                .locations(MIGRATION_LOCATION)
                .load();
        MigrateResult upgrade = toLatest.migrate();
        assertTrue(upgrade.success, "V5 升 V6 未成功");
        assertEquals(1, upgrade.migrationsExecuted, "升级路径应只新增 V6 一个迁移");
        assertDoesNotThrow(toLatest::validate, "升级后 Flyway 校验必须通过");

        try (Connection connection = openConnection(upgradeSchema)) {
            assertEquals(List.of("1", "2", "3", "4", "5", "6"), appliedVersions(connection),
                    "升级后版本序列应为 V1～V6");

            Map<String, String> collations = columnCollations(connection);
            for (String qualified : EXACT_COLUMNS) {
                assertEquals(EXACT_COLLATION, collations.get(qualified),
                        qualified + " 升级后必须改为逐字符比较");
            }

            assertEquals(Map.of(
                            "users.openid", "Db02Upgrade-Openid",
                            "file_objects.object_key", "db02/Upgrade/Key.BIN",
                            "refresh_tokens.device_id", "Db02Upgrade-Device",
                            "orders.order_no", "Db02Upgrade-No",
                            "orders.client_request_id", "Db02Upgrade-Req"),
                    storedIdentifiers(connection, userId, itemId),
                    "升级必须逐字符保留标识符，不得 trim、lowercase 或截断");

            assertEquals(rowCountsBefore, rowCounts(connection), "升级不得改变任何表的行数");
            assertEquals(BUSINESS_TABLES, baseTables(connection), "升级不得增减业务表");
            assertEquals(definitionsBefore, columnDefinitions(connection, TOUCHED_TABLES),
                    "V6 只能改排序规则：列类型、长度、字符集、可空性与默认值必须全部保持不变");
            assertEquals(indexesBefore, indexesOf(connection, TOUCHED_TABLES),
                    "升级不得丢失或改写既有索引");
            assertEquals(foreignKeysBefore, foreignKeys(connection), "升级不得丢失或改写既有外键");

            Map<String, Integer> checksumsAfter = migrationChecksums(connection);
            checksumsBefore.forEach((version, checksum) -> assertEquals(checksum, checksumsAfter.get(version),
                    "V" + version + " 的校验和不得变化——V6 不得以任何方式改写已执行迁移"));
        }

        MigrateResult second = toLatest.migrate();
        assertEquals(0, second.migrationsExecuted, "升级到 V6 后再迁移一次不应再执行任何版本");
        assertEquals(0, toLatest.info().pending().length, "升级到 V6 后不应存在待执行版本");
        assertDoesNotThrow(toLatest::validate, "重复迁移后 Flyway 校验必须通过");
    }

    // ------------------------------------------------------------------
    // 数据源解析与安全边界
    // ------------------------------------------------------------------

    private static void resolveDataSource() throws SQLException {
        String externalUrl = System.getProperty(URL_PROPERTY);
        if (externalUrl == null || externalUrl.isBlank()) {
            container = new MySQLContainer("mysql:8.0")
                    .withDatabaseName(CONTAINER_DATABASE)
                    .withUsername(CONTAINER_USERNAME)
                    .withPassword(CONTAINER_PASSWORD)
                    .withUrlParam("allowPublicKeyRetrieval", "true")
                    .withUrlParam("useSSL", "false");
            container.start();
            username = container.getUsername();
            password = container.getPassword();
            adminUsername = CONTAINER_ADMIN_USERNAME;
            adminPassword = container.getPassword();
            splitUrl(container.getJdbcUrl());
        } else {
            username = requiredSetting(USERNAME_PROPERTY, USERNAME_ENV);
            password = optionalSetting(PASSWORD_PROPERTY, PASSWORD_ENV);
            adminUsername = username;
            adminPassword = password;
            assertSafeExternalTarget(externalUrl, username, password, System.getProperty(CONFIRM_PROPERTY));
            splitUrl(externalUrl);
        }
    }

    /**
     * 把锚点 URL 拆成实例前缀与 URL 参数：MySQL 的 JDBC URL 中 schema 恒为最后一段路径，
     * 因此去掉查询串后截到最后一个 {@code /} 即可，绝不留住锚点 schema。
     */
    private static void splitUrl(String anchorUrl) {
        String withoutQuery = anchorUrl;
        int query = anchorUrl.indexOf('?');
        if (query >= 0) {
            instanceParameters = anchorUrl.substring(query + 1);
            withoutQuery = anchorUrl.substring(0, query);
        } else {
            instanceParameters = "";
        }
        int slash = withoutQuery.lastIndexOf('/');
        instancePrefix = withoutQuery.substring(0, slash + 1);
    }

    /**
     * 外部模式的前置保护。全部通过才允许继续；本方法只读，不执行任何 DDL/DML。
     *
     * <p>本测试不会在锚点 schema 内写入任何对象：所有读写都发生在 {@link #createDisposableSchema}
     * 建立的 {@code db02_it_*} 临时 schema 中。
     */
    static void assertSafeExternalTarget(String url, String user, String secret, String confirmation) {
        String token = confirmation == null ? null : confirmation.trim();
        if (!EXTERNAL_CONFIRMATION.equals(token) && !FlywayMigrationIT.EXTERNAL_CONFIRMATION.equals(token)) {
            throw new IllegalStateException("外部 MySQL 模式要求显式确认目标为专用测试库：请传入 -D"
                    + CONFIRM_PROPERTY + "=" + EXTERNAL_CONFIRMATION + "（或与 DB-01 用例共用 "
                    + FlywayMigrationIT.EXTERNAL_CONFIRMATION + "）");
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
        } catch (SQLException failure) {
            throw new IllegalStateException("外部 MySQL 模式无法连接目标实例：" + failure.getMessage(), failure);
        }
    }

    /**
     * 在目标实例上建立一个只属于本次运行的临时 schema，名称为 {@code prefix_<12 位随机后缀>}。
     *
     * <p>容器模式必须用管理员账号建库：镜像只为业务账号授予默认 schema 的权限，业务账号执行
     * {@code CREATE DATABASE} 会被拒绝。建库后把新 schema 的权限授予业务账号，使后续迁移与断言
     * 都以业务账号的受限身份执行。外部模式由调用方账号自行建库。
     */
    private static String createDisposableSchema(String prefix) throws SQLException {
        String schema = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection connection = DriverManager.getConnection(adminUrl(), adminUsername, adminPassword);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` "
                    + "CHARACTER SET utf8mb4 COLLATE " + TABLE_DEFAULT_COLLATION);
            if (!adminUsername.equals(username)) {
                statement.execute("GRANT ALL PRIVILEGES ON `" + schema + "`.* TO '" + username + "'@'%'");
            }
        }
        return schema;
    }

    /** 不带 schema 的实例级 URL，只用于建库；不指定默认库，因此不会碰到锚点 schema。 */
    private static String adminUrl() {
        return instancePrefix + (instanceParameters.isEmpty() ? "" : "?" + instanceParameters);
    }

    private static String schemaUrl(String schema) {
        return instancePrefix + schema + (instanceParameters.isEmpty() ? "" : "?" + instanceParameters);
    }

    private static Flyway flywayFor(String schema) {
        return Flyway.configure()
                .dataSource(schemaUrl(schema), username, password)
                .locations(MIGRATION_LOCATION)
                .load();
    }

    private static Connection openConnection(String schema) throws SQLException {
        return DriverManager.getConnection(schemaUrl(schema), username, password);
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

    // ------------------------------------------------------------------
    // 元数据查询
    // ------------------------------------------------------------------

    private static List<String> baseTables(Connection connection) throws SQLException {
        return query(connection, "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                + "AND table_name <> '" + FLYWAY_HISTORY_TABLE + "' ORDER BY table_name");
    }

    private static List<String> appliedVersions(Connection connection) throws SQLException {
        List<String> versions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL "
                        + "ORDER BY installed_rank");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                assertTrue(resultSet.getBoolean("success"),
                        "迁移 V" + resultSet.getString("version") + " 未成功");
                versions.add(resultSet.getString("version"));
            }
        }
        return versions;
    }

    private static Map<String, Integer> migrationChecksums(Connection connection) throws SQLException {
        Map<String, Integer> checksums = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version, checksum FROM flyway_schema_history WHERE version IS NOT NULL "
                        + "ORDER BY installed_rank");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                checksums.put(resultSet.getString("version"), resultSet.getInt("checksum"));
            }
        }
        return checksums;
    }

    private static Map<String, String> columnCollations(Connection connection) throws SQLException {
        Map<String, String> collations = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_name, column_name, collation_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND collation_name IS NOT NULL "
                        + "ORDER BY table_name, ordinal_position");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                collations.put(resultSet.getString("table_name") + "." + resultSet.getString("column_name"),
                        resultSet.getString("collation_name"));
            }
        }
        return collations;
    }

    /** 列定义指纹：类型、字符集、可空性、默认值。排序规则不在其中，用于证明 V6 只改了排序规则。 */
    private static Map<String, String> columnDefinitions(Connection connection, List<String> tables)
            throws SQLException {
        Map<String, String> definitions = new LinkedHashMap<>();
        for (String table : tables) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT column_name, column_type, character_set_name, is_nullable, column_default "
                            + "FROM information_schema.columns WHERE table_schema = DATABASE() "
                            + "AND table_name = ? ORDER BY ordinal_position")) {
                statement.setString(1, table);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        definitions.put(table + "." + resultSet.getString("column_name"),
                                resultSet.getString("column_type") + "|"
                                        + resultSet.getString("character_set_name") + "|"
                                        + resultSet.getString("is_nullable") + "|"
                                        + resultSet.getString("column_default"));
                    }
                }
            }
        }
        return definitions;
    }

    private static Map<String, String> indexesOf(Connection connection, List<String> tables)
            throws SQLException {
        Map<String, String> indexes = new LinkedHashMap<>();
        for (String table : tables) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index) "
                            + "FROM information_schema.statistics WHERE table_schema = DATABASE() "
                            + "AND table_name = ? GROUP BY index_name, non_unique ORDER BY index_name")) {
                statement.setString(1, table);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        indexes.put(table + "." + resultSet.getString(1),
                                resultSet.getInt("non_unique") + ":" + resultSet.getString(3));
                    }
                }
            }
        }
        return indexes;
    }

    private static List<String> foreignKeys(Connection connection) throws SQLException {
        return query(connection, "SELECT CONCAT(table_name, '.', constraint_name, '->', "
                + "referenced_table_name, '.', column_name, '=', referenced_column_name) "
                + "FROM information_schema.key_column_usage WHERE table_schema = DATABASE() "
                + "AND referenced_table_name IS NOT NULL ORDER BY table_name, constraint_name");
    }

    private static Map<String, Integer> rowCounts(Connection connection) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : BUSINESS_TABLES) {
            counts.put(table, (int) count(connection, "SELECT COUNT(*) FROM `" + table + "`"));
        }
        return counts;
    }

    private static Map<String, String> storedIdentifiers(Connection connection, long userId, long itemId)
            throws SQLException {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("users.openid", scalar(connection,
                "SELECT openid FROM users WHERE id = " + userId));
        values.put("file_objects.object_key", scalar(connection,
                "SELECT object_key FROM file_objects WHERE owner_id = " + userId));
        values.put("refresh_tokens.device_id", scalar(connection,
                "SELECT device_id FROM refresh_tokens WHERE user_id = " + userId));
        values.put("orders.order_no", scalar(connection,
                "SELECT order_no FROM orders WHERE item_id = " + itemId));
        values.put("orders.client_request_id", scalar(connection,
                "SELECT client_request_id FROM orders WHERE item_id = " + itemId));
        return values;
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

    private static String scalar(Connection connection, String sql) throws SQLException {
        List<String> values = query(connection, sql);
        assertEquals(1, values.size(), "期望恰好一行: " + sql);
        return values.get(0);
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next(), "计数查询未返回结果: " + sql);
            return resultSet.getLong(1);
        }
    }

    /** 按精确值查询指定列，返回命中的行数，用于证明等值比较未折叠大小写或空格。 */
    private static long rowsWith(Connection connection, String fromClause, String column, String value)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                fromClause + " WHERE " + column + " = ?")) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                long rows = 0;
                while (resultSet.next()) {
                    rows++;
                }
                return rows;
            }
        }
    }

    // ------------------------------------------------------------------
    // 样例数据
    // ------------------------------------------------------------------

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

    private static long insertItem(Connection connection, long sellerId) throws SQLException {
        String sql = "INSERT INTO `items` (`seller_id`, `category_id`, `campus_id`, `title`, `description`, `price`, "
                + "`original_price`, `condition_level`, `status`, `admin_lock`, `off_shelf_reason`, `view_count`, "
                + "`favorite_count`, `version`, `published_at`, `created_at`, `updated_at`) "
                + "VALUES (?, 101, 1, 'DB-02 集成测试商品', 'DB-02 标识符比较集成测试使用的商品描述', 10.00, NULL, "
                + "'GOOD', 'ON_SALE', 0, NULL, 0, 0, 0, NULL, ?, ?)";
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

    private static void insertFileObject(Connection connection, long ownerId, String objectKey)
            throws SQLException {
        String sql = "INSERT INTO `file_objects` (`owner_id`, `biz_type`, `object_key`, `original_name`, "
                + "`content_type`, `size_bytes`, `sha256`, `status`, `created_at`) "
                + "VALUES (?, 'ITEM_IMAGE', ?, 'sample.png', 'image/png', 1024, ?, 'UPLOADED', ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            statement.setString(2, objectKey);
            statement.setString(3, "a".repeat(64));
            statement.setTimestamp(4, now());
            statement.executeUpdate();
        }
    }

    private static void insertRefreshToken(Connection connection, long userId, String deviceId)
            throws SQLException {
        String sql = "INSERT INTO `refresh_tokens` (`user_id`, `token_hash`, `device_id`, `expires_at`, "
                + "`revoked_at`, `replaced_by_token_id`, `created_at`) VALUES (?, ?, ?, ?, NULL, NULL, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, (UUID.randomUUID().toString() + UUID.randomUUID().toString())
                    .replace("-", ""));
            statement.setString(3, deviceId);
            statement.setTimestamp(4, now());
            statement.setTimestamp(5, now());
            statement.executeUpdate();
        }
    }

    private static void insertOrder(Connection connection, long itemId, long buyerId, long sellerId,
                                    String orderNo, String clientRequestId) throws SQLException {
        String sql = "INSERT INTO `orders` (`order_no`, `item_id`, `buyer_id`, `seller_id`, "
                + "`client_request_id`, `amount`, `trade_mode`, `status`, `version`, `created_at`, `updated_at`) "
                + "VALUES (?, ?, ?, ?, ?, 10.00, 'OFFLINE', 'PENDING_CONFIRMATION', 0, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderNo);
            statement.setLong(2, itemId);
            statement.setLong(3, buyerId);
            statement.setLong(4, sellerId);
            statement.setString(5, clientRequestId);
            setNow(statement, 6, 7);
            statement.executeUpdate();
        }
    }

    private static Map<String, Long> userIdsByOpenid(Connection connection, String... openids)
            throws SQLException {
        Map<String, Long> found = new LinkedHashMap<>();
        for (String openid : openids) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, openid FROM users WHERE openid = ?")) {
                statement.setString(1, openid);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next(), "未按精确值查回 openid=" + openid);
                    assertEquals(openid, resultSet.getString("openid"), "读出的 openid 被改写");
                    found.put(openid, resultSet.getLong("id"));
                    assertTrue(!resultSet.next(), "精确值 openid=" + openid + " 命中了多行");
                }
            }
        }
        return found;
    }

    private static String storedOpenid(Connection connection, String openid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT openid FROM users WHERE openid = ?")) {
            statement.setString(1, openid);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "未查回 openid=" + openid);
                return resultSet.getString(1);
            }
        }
    }

    private static String storedObjectKey(Connection connection, long ownerId, String objectKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT object_key FROM file_objects WHERE owner_id = ? AND object_key = ?")) {
            statement.setLong(1, ownerId);
            statement.setString(2, objectKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "未查回 object_key=" + objectKey);
                return resultSet.getString(1);
            }
        }
    }

    private static void setNow(PreparedStatement statement, int firstIndex, int secondIndex)
            throws SQLException {
        Timestamp currentTimestamp = now();
        statement.setTimestamp(firstIndex, currentTimestamp);
        statement.setTimestamp(secondIndex, currentTimestamp);
    }

    private static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }
}
