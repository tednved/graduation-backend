# BE-MVP-01 交接报告

## 1. 基本信息

| 项 | 值 |
| --- | --- |
| 任务 ID | BE-MVP-01 |
| 分支 | `feat/BE-MVP-01-core` |
| 基线 | `origin/main` = `e15baa7`（API-01 单校区冻结版；数据库基线 Flyway V6） |
| PR | [#5](https://github.com/tednved/graduation-backend/pull/5)（Draft） |
| 状态 | **IN_PROGRESS** —— 代码与真实 MySQL 测试完成；前后端真实联调与微信开发者工具 GUI 验收未执行 |
| 契约源 | `backend/openapi.yaml`（本 PR 未修改） |

本任务一次完成公共底座、登录资料、本地文件、MANUAL 认证与分类管理，闭合「登录 → 资料 → 认证 → 分类」。

---

## 2. 实现结果

| 范围 | 落实 |
| --- | --- |
| 公共底座 | `ApiResponse`/`ApiError` 信封；§7.2 全部 25 个错误码与 HTTP 状态 1:1；`X-Request-Id` 贯通（`^[A-Za-z0-9_-]{1,64}$`，非法值重新生成并回写响应头，MDC 在 `finally` 清理）；HS256 JWT，Access 15 分钟、Refresh 30 天；标识符按十进制字符串、金额两位小数、时间 UTC 毫秒 `Z`；注入式 `Clock` |
| 登录 | Mock 微信登录；单校区按 `code=MAIN` 自动绑定，登录与刷新都不接受校区字段；刷新轮换（旧行置 `revokedAt` 并写 `replacedByTokenId`）；已轮换令牌重放视为泄露——`REQUIRES_NEW` 独立事务撤销该设备全部有效令牌后再返回 401，避免撤销被调用方回滚吃掉；登出幂等 |
| 资料 | 只允许改昵称/头像/电话；契约外字段由 `fail-on-unknown-properties` 拦成 400 而非静默忽略；`/users/{id}/public` 匿名可读且不含手机号；受保护请求每次重新读库取最新用户状态 |
| 文件 | 大小 → 文件头（magic bytes） → 声明 MIME → 扩展名四步校验；ITEM_IMAGE 5 MB / AVATAR 2 MB；`objectKey` 完全由服务端生成（业务类型/年月/16 字节随机），响应只给 `/media/{fileId}`，不暴露存储键；落盘成功但入库失败时补偿删除，不留孤儿对象；头像绑定校验归属、业务类型与可用性 |
| 认证 | 仅 MANUAL；明文只在写入前脱敏一次（`张*` / `20******34`），库内与日志都不出现明文；「同一用户最多一个 PENDING」通过**锁用户行**实现——单纯对 `certifications` 做 `SELECT ... FOR UPDATE` 在无 PENDING 时锁不到任何行，并发提交仍会各插一条；审核走 `findByIdForUpdate`，重复审核返回 `CERTIFICATION_ALREADY_REVIEWED`；管理端列表走 MyBatis-Plus 物理分页 |
| 分类 | 两级树只含 `ENABLED`，二级节点不返回 `children`；父分类非一级或已停用时创建被拒；禁止通过更新改层级；同级重名拒绝；停用自身有在售商品返回 `CATEGORY_IN_USE`，停用一级时级联停用无在售商品的子分类，启用一级时级联恢复；创建/修改/启停均写审计 |

---

## 3. 交付物与修改文件

新增 `src/main/java/com/graduation/backend/{common,auth,user,file,certification,category}/**`（含 `admin/api/AdminCertificationController.java`、`category/api/AdminCategoryController.java`），修改 `BackendApplication.java`（加 `@ConfigurationPropertiesScan`）与 `src/main/resources/application.properties`（数据源、Flyway、`ddl-auto=validate`、上传上限、业务开关）。测试侧新增 `CoreApiFlowTests`、`support/RealMySqlTestBase`、`support/RealMySqlTestConfiguration`、`src/test/resources/application.properties`，改写 `BackendApplicationTests`。

未改动：`openapi.yaml`、`src/main/resources/db/migration/**`（V1～V6）、`pom.xml`、`TestcontainersConfiguration`、`TestBackendApplication`、`database/FlywayMigrationIT`、`database/IdentifierCollationIT`。

---

## 4. 验证证据

本机无 Docker，因此**不走 Testcontainers**：改为真实本机 MySQL 的一次性 schema `graduation_mvp_it`。`RealMySqlTestConfiguration` 在执行 `flyway.clean()` 之前强制校验 schema 名必须以 `_it` 结尾，否则直接抛错、不做任何修改，避免误删非测试数据。V1～V6 因此在空 schema 上真实重放，并由 `ddl-auto=validate` 校验实体映射。

| 命令 | 结果 |
| --- | --- |
| `DB_IT_USERNAME=… DB_IT_PASSWORD=… ./mvnw -B verify` | **EXIT 0**，`Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` |
| `BackendApplicationTests` | 2/2：空库迁移 V1～V6 全成功；V5 只写入 `code=MAIN` 的启用校区 |
| `CoreApiFlowTests` | 14/14：MockMvc 走**真实安全过滤链**与真实 MySQL，鉴权用真实签发的 JWT |
| `powershell -File scripts/validate-openapi.ps1` | **EXIT 0**，51 个操作结构/引用/枚举/错误码校验通过 |
| 启动配置 | `spring.jpa.hibernate.ddl-auto=validate`、`spring.flyway.clean-disabled=true`（生产）、多部分上限 5 MB/6 MB |

`CoreApiFlowTests` 覆盖主链与失败路径：登录/资料/文件/认证/分类主链；无 Token 401；禁用用户 403；Refresh 重放 401 且同设备令牌被撤销；重复 PENDING 409；非管理员审核 403；非法类型 400；超大文件 413；三级分类 400。

---

## 5. 审查中发现并修正的问题

代理实现后由审核者复查，修正了以下 5 处（均已包含在本 PR）：

1. **测试根本没跑 Spring Security 过滤链**（导致受保护接口在测试里一律 401）。Boot 4 把 `@AutoConfigureMockMvc` 移入独立测试模块，本工程未依赖 `spring-boot-starter-webmvc-test`，而 `webAppContextSetup` 只装载注册为 `Filter` Bean 的过滤器，安全链是通过 Servlet 容器初始化器注册的。已用 `SecurityMockMvcConfigurers.springSecurity()` 显式挂上。
2. **契约理解错误 ×3**：测试断言 `$.data.campus.code`，但契约 `CampusRef` 只有 `id`/`name`。后端是对的，测试写错了；改为按库内 `code=MAIN` 的校区 ID 断言。
3. **`version` 断言错误**：期望 `0`，实际 `1`——登录写 `lastLoginAt` 会把新用户的乐观锁版本推到 1；改为断言非负整数。
4. **`LocalFileStorage` 未使用注入的 `Clock`**：日期分区直接用 `LocalDate.now(ZoneOffset.UTC)`，与「业务时间统一来自注入的 Clock」约定不符；已改为注入 `Clock`。
5. 删除代理遗留的临时诊断类 `DiagTests`（会把令牌打进行日志，且本身设计为抛错）。

---

## 6. 已知残留与未覆盖

- **`CATEGORY_IN_USE` 未覆盖**：需要 `items` 表存在 `ON_SALE`/`RESERVED` 商品才能构造，属商品里程碑数据；代码路径已实现，本阶段无法用真实数据验证。
- **刷新轮换未加行锁**：并发用同一刷新令牌请求，理论上可各自成功一次（都存在 `isRevoked()==false` 的窗口）。不影响本里程碑验收，商品/交易里程碑前建议补 `PESSIMISTIC_WRITE`。
- **管理员账号**：本里程碑没有管理员管理接口，V5 也不种子任何用户。测试用例登录后把 `role` 改成 `ADMIN`，用于验证「权限每次请求重新读库、提权立即生效」。演示前需手工提升一次：
  ```sql
  UPDATE users SET role = 'ADMIN' WHERE openid = '<Mock 登录产生的 openid>';
  ```
  （`role` 取值受 `chk_users_role` 约束，只允许 `USER`/`ADMIN`。）是否需要新增一个 V7 迁移种子一个开发用管理员，请项目负责人决定。
- **真实联调与 GUI 验收**：未执行。前端 FE-MVP-01 已完成，联调脚本已备好。

---

## 7. 下一任务输入

- 可复用：`common/web`（信封与异常）、`common/security`（JWT 签发/校验、当前用户）、`common/audit`、`file`（`FilePolicy`/`FileStorage`/`FileService`）、`RealMySqlTestBase`（鉴权 + 真实 MySQL 的 MockMvc 基类）。
- 接入注意：
  - 新增受保护接口无需改 `SecurityConfig`；`anyRequest().authenticated()` 已兜底，只有契约标注 `security: []` 的读接口才加入 `PUBLIC_GET`。
  - 写操作请在 ApplicationService 上加 `@Transactional`；状态变更走领域方法。
  - 商品图片/商品表接入后，`CategoryRepository.countBlockingItems` 就是 `CATEGORY_IN_USE` 的数据来源。
  - 测试需要真实 MySQL：设置 `DB_IT_USERNAME`/`DB_IT_PASSWORD`（可选 `DB_IT_URL`），否则 `@EnabledIf("databaseConfigured")` 会整体跳过而不是失败。
