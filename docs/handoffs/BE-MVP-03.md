# BE-MVP-03 交接报告

## 1. 基本信息

| 项 | 值 |
| --- | --- |
| 任务 ID | BE-MVP-03 |
| 分支 | `feat/BE-MVP-03-trade` |
| 基线 | `origin/main` = `ce30414`（BE-MVP-02 合并后的日志提交；契约 API-01 冻结版 / Flyway V6） |
| PR | [#7](https://github.com/tednved/graduation-backend/pull/7)（Draft） |
| 状态 | **DONE** —— PR #7 已 squash 合并为 `b4b622d`；真实 MySQL 60/60、契约校验和负责人 GUI 运行验收通过 |
| 契约源 | `backend/openapi.yaml`（**本 PR 未修改契约**，未新增迁移） |

本任务闭合「下单 → 接单/拒单 → 交付 → 收货」订单闭环，并补齐评价、站内消息与未读红点、必要管理端（用户分页与禁用/恢复、商品分页、审计查询）。

---

## 2. 实现结果

| 范围 | 落实 |
| --- | --- |
| 下单 | `POST /orders`：要求 ACTIVE + 认证 `APPROVED`（否则 403 `USER_CERTIFICATION_REQUIRED`）；不能购买自己商品（409 `ITEM_SELF_PURCHASE`）；金额与商品信息取快照落 `order_snapshots`，事后改价不影响已生成订单；商品转 `RESERVED` 与订单创建同事务 |
| 幂等 | 先锁买家行（`requireActiveUserForUpdate`）再按 `(buyer_id, client_request_id)` 查已有订单：同键同商品返回原订单 **200**（不产生第二笔、不新增事件、不重复预留），同键换商品 409 `ORDER_DUPLICATE_REQUEST`；首次创建 **201** |
| 状态机 | `PENDING_CONFIRMATION` →（卖家）`CONFIRMED` →（卖家）`PENDING_RECEIPT` →（买家）`COMPLETED`；`REJECTED`/`CANCELLED` 为终态。身份不符 403 `ORDER_OPERATION_FORBIDDEN`，状态不符 409 `ORDER_ILLEGAL_STATUS_TRANSITION`。**身份判定一律先于状态判定**：非参与方对任何状态的订单都只得到 403，不泄漏进度（见 §11.2） |
| 事件时间线 | 每次流转追加一条 `order_events`（`fromStatus` 首条为 `null`、`toStatus`、`action`、`operator`、`remark`），详情按时间正序返回 |
| 商品状态联动 | 接单/交付期间保持 `RESERVED`；收货转 `SOLD`；拒单与取消释放回 `ON_SALE`。商品状态与订单状态永远同事务提交，不出现「订单已完成但商品仍可下单」的中间态 |
| 我的订单 | `GET /users/me/orders`：`side=BUY` 对端为卖家、`side=SELL` 对端为买家，可按 `status` 筛选；MyBatis-Plus 物理分页 |
| 评价 | `POST /reviews`：仅订单双方且订单 `COMPLETED` 可评（否则 409 `REVIEW_NOT_ALLOWED`）；`revieweeId` 由服务端按订单对端推导，请求体无此字段；重复评价 409 `REVIEW_ALREADY_EXISTS`；`rating` 1~5 由 Bean Validation 拦为 400；可选 `version` 校验 |
| 评价读取 | `GET /orders/{id}/review-eligibility`（参与方；非参与方 403）、`GET /users/{id}/reviews`（公开，可 `rating` 筛选）、`GET /users/{id}/credit`（公开，平均分/评价数/1~5 分布） |
| 信用汇总 | 落库值由数据库按 `VISIBLE` 评价重算，写路径**先锁订单行**再聚合——订单是双方评价唯一的公共对象，避免两次互评互相覆盖；重算幂等，平均分两位小数，无评价时 `0.00` |
| 站内消息 | 订单状态变化（下单/接单/拒单/取消/交付/收货）与管理员下架均生成消息；业务事务内发布 `NotificationMessage`，`@TransactionalEventListener(AFTER_COMMIT)` 监听，新事务写库由独立 Bean `NotificationWriter` 承担，监听器在外层捕获**整个代理调用**的异常，写入失败只告警不回滚业务（见 §11.1） |
| 消息接口 | `GET /notifications`（可按 `read`/`type` 筛选）、`GET /notifications/unread-count`、`PUT /notifications/{id}/read`（204，幂等，他人的消息按 404 处理）、`PUT /notifications/read-all`（返回本次真正由未读转已读的 `updatedCount`） |
| 管理端用户 | `GET /admin/users`（关键字/状态筛选；响应**不含 `openid`**）、`POST /admin/users/{id}/disable`、`/enable`；禁用**连带撤销该用户全部未撤销 Refresh Token**（同一事务），禁用后旧 Access Token 访问任意受保护接口按 403 `USER_DISABLED`。管理员不能禁用自己（403） |
| 管理端商品 | `GET /admin/items`：不受 `ON_SALE` 限制，支持多状态/卖家/关键字，返回 `DRAFT` 等非在售商品与 `adminLock`/`offShelfReason` |
| 审计 | 管理端写操作记录 `audit_logs`（操作人、动作、目标类型/ID、`requestId`、`detail`），`GET /admin/audit-logs` 可按动作/操作人/目标查询 |

并发与一致性取舍：

- 锁顺序固定：创建订单 = 买家用户行 → 商品行；确认/拒单/取消/交付/收货 = 订单行 → 商品行；评价 = 订单行。全链路无反向获取，不存在死锁环。
- 所有会改 `version` 的写路径先 `findByIdForUpdate` 悲观锁定，再显式比对 `version`；回显前显式 flush（沿用 BE-MVP-02 的 `assembleAfterFlush` 约定）。MyBatis 查询不会触发 JPA 自动 flush，`AdminUserService.reload` 在领域变更后先 `repository.flush()` 再重查。
- 管理端判权只在应用层事务内走 `UserService.requireAdmin`（403 `AUTH_FORBIDDEN`），未引入 `@PreAuthorize` 与角色规则，与既有约定一致。

---

## 3. 交付物与修改文件

新增（主源）：

- `order/**`：`domain`（`Order`、`OrderStatus`、`OrderAction`、`OrderSide`、`TradeMode`、`OrderEvent`、`OrderSnapshot`、仓库接口）、`application`（`OrderApplicationService`、`OrderQueryService`、`OrderCreationResult`）、`api`（`OrderController` 及 5 个 DTO）、`query`（`OrderQueryMapper`、`OrderSummaryRow`）
- `review/**`：`domain`（`Review`、`ReviewStatus`、`ReviewRepository`）、`application`（`ReviewApplicationService`、`ReviewQueryService`）、`api`（`ReviewController` 及 4 个 DTO）、`query`（`ReviewQueryMapper`、`ReviewRow`、`RatingCountRow`）
- `notification/**`：`domain`（`Notification`、`NotificationType`、`NotificationRepository`）、`application`（`NotificationPublisher`、`NotificationWriter`、`NotificationMessage`、`NotificationApplicationService`、`NotificationQueryService`）、`api`（`NotificationController` 及 3 个 DTO）、`query`（`NotificationQueryMapper`、`NotificationRow`）
- `admin/**`：`AdminUserController`、`AdminAuditLogController`、`AdminUserService`、`AdminItemQueryService`、`AuditLogQueryService`、三个 query mapper 与 row、三个响应 DTO
- `common/web/LikePatterns.java`（关键字 `%`/`_` 转义，与 BE-MVP-02 的搜索同源）
- `src/main/resources/mapper/{order,review,notification,admin}/*.xml`（6 个）
- `src/test/java/com/graduation/backend/{order/OrderApiFlowTests,order/OrderConcurrencyTests,order/OrderTestSupport,review/ReviewFlowTests,notification/NotificationFlowTests,admin/AdminApiFlowTests}.java`

修改：

| 文件 | 改动 | 说明 |
| --- | --- | --- |
| `common/config/SecurityConfig.java` | `PUBLIC_GET` 增加 `/api/v1/users/*/reviews` 与 `/api/v1/users/*/credit` | **本任务唯一的跨模块行为改动**，契约把这两个读接口标为 `security: []`；用单段 `*` 精确放行，避免把 `/api/v1/users/me/**` 一并放行 |
| `user/domain/User.java` | 新增 `applyRating`、`disable`、`enable`、`isDisabled` | 纯新增领域方法；信用汇总与账号状态变更只走领域方法 |
| `auth/application/RefreshTokenRevoker.java` | 新增 `revokeAllForUser`（`Propagation.MANDATORY`） | 加入调用方事务，保证「禁用状态 + 令牌撤销 + 审计」一起提交 |
| `auth/domain/RefreshTokenRepository.java` | 新增 `findByUserIdAndRevokedAtIsNull` | 供上面的一次性撤销使用 |
| `item/application/ItemApplicationService.java` | `adminOffShelf` 发布卖家通知事件 | 事务内只发事件，提交后另起事务落库 |
| `admin/api/AdminItemController.java` | 增加 `GET /admin/items` | 关闭 MVP-02 残留 2 |
| `review/domain/Review.java` | `rating` 补 `@JdbcTypeCode(SqlTypes.TINYINT)` | **真实缺陷修复**，见 §6 |
| `notification/application/NotificationPublisher.java` | 去掉自身的 `@Transactional(REQUIRES_NEW)`，改为注入 `NotificationWriter` 并在监听器外层捕获 | 审查意见 2，见 §11.1 |
| `order/domain/Order.java` | `requireSeller` 由 `private` 改为 `public` | 审查意见 3：接单要同时判身份与商品状态，应用层必须先判身份。身份规则仍只有这一份实现 |
| `order/application/OrderApplicationService.java` | `confirm` 调整判定顺序：身份先于商品状态 | 审查意见 3，见 §11.2 |
| `notification/NotificationFlowTests.java` | 新增 `notificationWriteFailureIsIsolated` | 审查意见 2 的回归兜底 |
| `order/OrderApiFlowTests.java` | 新增 `confirmChecksIdentityBeforeItemState` | 审查意见 3 的回归兜底 |

未改动：`pom.xml`、`src/main/resources/db/migration/**`（V1～V6 只读未动，**无新增迁移**）、`openapi.yaml`、`frontend` 侧任何文件。表 `orders`/`order_events`/`order_snapshots`/`reviews`/`notifications`/`audit_logs` 均已在 V3/V4 建好。

---

## 4. 验证证据

| 命令 | 结果 |
| --- | --- |
| `DB_IT_USERNAME=<user> DB_IT_PASSWORD=<pwd> ./mvnw -B -o test` | **Tests run: 60, Failures: 0, Errors: 0, Skipped: 0**，`BUILD SUCCESS`，EXIT 0（2026-09-13 重跑） |
| `powershell -File scripts/validate-openapi.ps1` | **EXIT 0**（51 个操作 / 15 个枚举 / 25 个错误码全部通过结构与一致性校验） |
| `git diff --check origin/main...HEAD` | 无输出，EXIT 0 |

**关于「普通 test 是否需要排除 Docker IT」——不需要排除，也没有排除项。** 本仓库**没有** failsafe 插件，也没在 `pom.xml` 里配置 surefire，因此 surefire 只用默认匹配（`**/Test*.java`、`**/*Test.java`、`**/*Tests.java`、`**/*TestCase.java`），而 `FlywayMigrationIT` / `IdentifierCollationIT` 两个类名以 `IT` 结尾，**不匹配任何默认模式，普通 `./mvnw -B -o test` 根本不会加载它们**。

本轮证据：上面这条命令一次跑完只加载 9 个用例类，输出中**没有出现任何 Testcontainers/Docker 相关日志**，`target/surefire-reports` 也只生成这 9 个类的报告：

| 用例类 | 数量 |
| --- | --- |
| `BackendApplicationTests` | 2 |
| `CoreApiFlowTests` | 14 |
| `admin.AdminApiFlowTests` | 4 |
| `favorite.FavoriteFlowTests` | 5 |
| `item.ItemApiFlowTests` | 15 |
| `notification.NotificationFlowTests` | 5 |
| `order.OrderApiFlowTests` | 10 |
| `order.OrderConcurrencyTests` | 2 |
| `review.ReviewFlowTests` | 3 |
| **合计** | **60** |

若在 `target/surefire-reports` 下看到 `FlywayMigrationIT` / `IdentifierCollationIT` 的报告，那是**历史运行遗留的文件**（`mvn test` 只覆盖同名报告、不清理已删源码的报告），不代表本轮执行过。本轮已删除这三个类的残留报告（另含已删除的 `AnonymousProbeTests`），因此再次运行后 `surefire-reports` 里只会剩上表 9 个类。**统计用例数请以本轮新生成的报告为准。**

测试总数由 MVP-02 的 36 增至 **60**（新增 24 个用例：BE-MVP-03 功能用例 20 + 审查回归 4，其中并发用例 2）。真实 MySQL 用例在 `RealMySqlTestConfiguration` 下**每次上下文启动都从空 schema 重放 V1～V6 再 `ddl-auto=validate`**，因此本轮 60/60 同时覆盖「迁移可重放 + 实体映射与库结构一致」这一验收项。

安全自查：分支全量 diff 无口令/密钥/`session_key`/完整手机号学号；`tednved`/`0721` 仅出现在既有、负责人授权的 `src/main/resources/application.properties`；文档与测试只出现 `DB_IT_USERNAME=<user>` 这类占位符。

---

## 5. 测试覆盖（真实 MySQL，60/60 全绿）

本轮新增 24 个用例，全部端到端走 HTTP：

- `OrderApiFlowTests`（10）：`fullTradeChain`（下单 → 商品 `RESERVED`、买家 `allowedActions=[CANCEL]`、卖家 `[CONFIRM,REJECT]`；接单 → `[CANCEL,DELIVER]`；交付 → 空；收货 → `COMPLETED` + 商品 `SOLD` + 4 条事件 + 首条 `fromStatus` 为 `null`）；`idempotentCreateReplaysSameOrder`（201 → 200，同 ID/订单号/事件数，`orders` 行数仍为 1；换商品 409 `ORDER_DUPLICATE_REQUEST`）；`selfPurchaseAndCertificationRejected`；`unavailableItemRejected`（`ITEM_CONCURRENTLY_RESERVED` / `ITEM_NOT_AVAILABLE` / 404）；`unsupportedTradeModeRejected`（400 且商品仍在售）；`onlyParticipantsCanAccessOrder`（非参与方 403、管理员 200、匿名 401）；`illegalTransitionsRejected`（403 与 409 分流、商品保持 `RESERVED`）；`rejectAndCancelReleaseItem`（拒单与取消都释放商品，第三人可继续下单）；`myOrdersSeparatesSides`；**`confirmChecksIdentityBeforeItemState`**（订单走完使商品 `SOLD` 后，陌生人查详情与调接单**都**是 403 `ORDER_OPERATION_FORBIDDEN`，卖家才是 409 —— 见 §11.2）。
- `OrderConcurrencyTests`（2，**真并发**）：两个请求各占独立线程与独立事务，`CountDownLatch` 同点起跑。`concurrentBuyersOnlyOneWins`（状态集合恰为 `{201, 409}`，败者码为 `ITEM_CONCURRENTLY_RESERVED`，`orders` 只有 1 行，商品 `RESERVED`）；`concurrentSameRequestIdProducesOneOrder`（集合恰为 `{201, 200}`、同一订单 ID、买家只有 1 笔订单、只有 1 条 `CREATE` 事件）。断言的是**不变量**而非时序，调度抖动不会让它随机失败，而移除悲观锁会稳定失败。见 §11.3。
- `ReviewFlowTests`（3）：`bothSidesReviewAndCreditAggregated`（被评价人按对端推导、重复 409、双方信用各自聚合、分布补零、公开列表匿名可读且可筛选）；`reviewRequiresCompletedOrderAndParticipation`；`reviewVersionConflict`（版本不一致 409，一致 201，未填内容存 `null` 而非空串）。
- `NotificationFlowTests`（5）：`orderLifecycleGeneratesNotifications`（下单通知卖家、接单通知买家、未读数互不干扰、标记已读幂等、他人的消息 404、匿名 401）；`markAllReadCountsOnlyChanges`；`adminOffShelfNotifiesSeller`（`SYSTEM` + `bizType=ITEM` + 内容含原因与标题）；`disabledUserLosesAccess`（旧令牌 403、刷新令牌全撤销、禁用后不能重新登录、恢复后可登录）；**`notificationWriteFailureIsIsolated`**（一条消息写失败时业务事务照常提交、同批其余消息照常落库 —— 见 §11.1）。
- `AdminApiFlowTests`（4）：`userListHidesOpenidAndRequiresAdmin`（整响应体不含 `openid` 字符串）；`disableRulesAndCertificationKept`（不能禁用自己、禁用不改变认证状态）；`itemListIncludesNonOnSaleItems`；`auditLogsRecordAdminWrites`。

测试脚手架 `OrderTestSupport` 的关键设计：真实 MySQL 用例**不做事务回滚**，同一个 mock code 两次运行是同一账号，上一轮攒下的订单与消息会把「恰好 N 条」断言撑爆。因此重写 `login`，给每个 code 追加进程级 `RUN_ID`（管理员账号同样每轮新建），让用例只看到自己造的数据。用例类不加 `@Transactional`——消息在 `AFTER_COMMIT` 之后才落库，断言必须是提交后的真实状态。

---

## 6. 执行测试暴露的真实缺陷

`BackendApplicationTests` 与全部新用例首次运行时 `ApplicationContext` 加载失败：

```
org.hibernate.tool.schema.spi.SchemaManagementException: Schema validation: wrong column type
encountered in column [rating] in table [reviews];
found [tinyint unsigned (Types#TINYINT)], but expecting [integer (Types#INTEGER)]
```

这是**产品缺陷而非测试问题**：`reviews.rating` 是 `TINYINT UNSIGNED`（V4），实体未声明 JDBC 类型时 Hibernate 按 `Integer` 推断为 `INTEGER`，`ddl-auto=validate` 下应用**根本无法启动**。已按仓库既有做法（`item/domain/ItemImage.sortNo` 与 `item_images.sort_no` 的同款映射）补 `@JdbcTypeCode(SqlTypes.TINYINT)`。这类缺陷只有真实 schema + `validate` 才能暴露，是真实库测试脚手架的直接收益。

另有 3 处**测试写法缺陷**（非实现问题），已修正：

1. `idempotentCreateReplaysSameOrder` 最初断言「重放响应体与首次完全相同」——响应外层信封带每请求唯一的 `requestId` 与 `timestamp`，不可能相等。改为断言同 ID / 同订单号 / 同事件数 / 同状态，断言强度不减且不依赖信封字段。
2. `NotificationFlowTests` 的「匿名访问」断言误留了 `Authorization` 头，期望 401 实际 200。定位时先写了一次性探针（裸 `GET /notifications`，在带凭证请求前后各一次）确认两次都是 401、**不存在 SecurityContext 泄漏**，才归因为断言自身的问题并去掉该头、删除探针。
3. `ReviewFlowTests` 里 `completeOrder` 会对已 `RESERVED` 的商品再下一次单（409，`data` 为 `null`）。拆成 `completeOrder`（下单 + 推进）与 `advanceToCompleted`（只推进），用例改用后者。

`FlywayMigrationIT` / `IdentifierCollationIT` 仍依赖 Testcontainers，本机无 Docker 环境，**维持现状不可运行**（与 MVP-02 相同，非本任务引入）。需要特别说明的是：它们对本轮命令**完全不构成干扰**——两个类名以 `IT` 结尾，既不匹配 surefire 的默认包含模式，仓库里也没有 failsafe 插件或自定义 surefire 配置，所以 `./mvnw -B -o test` 既不会加载它们，也不会因为它们产生 error。详见 §4 的证据与报告清单。V1～V6 重放 + `validate` 的覆盖由 `RealMySqlTestConfiguration` 提供，见 §4。

---

## 7. 需审查确认的契约判断（契约未定义，实现自行裁定）

1. **`SecurityConfig.PUBLIC_GET` 新增两项**：`/api/v1/users/*/reviews` 与 `/api/v1/users/*/credit` 在 `openapi.yaml` 中均为 `security: []`，但同前缀的 `/api/v1/users/me/**` 必须登录，因此刻意用**单段 `*`** 而非 `**`。`ReviewFlowTests` 中的匿名读断言是这条放行的回归兜底。
2. **消息 `bizType` 取值未冻结**：契约定义了 `NotificationType` 枚举，但 `bizType` 是自由字符串。实现取两个字面量 `ORDER`（订单类消息，`bizId` = 订单 ID）与 `ITEM`（下架通知，`bizId` = 商品 ID），常量在 `NotificationMessage.BIZ_TYPE_ORDER` / `BIZ_TYPE_ITEM`。**这是一处未冻结的约定，前端如需按 `bizType` 跳转请以本表为准**；若希望进契约请在下游任务补 `enum`。
3. **`CreateReviewRequest.version` 不匹配 → 409 `REVIEW_NOT_ALLOWED`**：契约给评价模块只定义了 `REVIEW_NOT_ALLOWED` 与 `REVIEW_ALREADY_EXISTS` 两个 409，没有版本冲突专属错误码，故复用前者并附消息「订单已更新，请刷新后重试」。`version` 为可选，不传则跳过校验。
4. **消息写入失败不回滚业务**：`NotificationPublisher` 捕获异常只记 `warn`。契约要求订单/下架操作不被消息拖累，代价是极端情况下会丢消息且只有日志。属有意取舍。
5. **`replaceAll` 语义的信用聚合**：`User.applyRating` 用数据库算出的聚合值**覆盖**而非增量累加，因此重复执行幂等；这也是「先锁订单行」的收益所在。

---

## 8. 已关闭的 MVP-02 残留

| MVP-02 残留 | 状态 |
| --- | --- |
| 残留 2：`GET /admin/items` 未实现（`x-owner-task: BE-10`） | **已关闭**：`AdminItemController.list` + `AdminItemQueryService` + `AdminItemQueryMapper.xml`，用例 `AdminApiFlowTests.itemListIncludesNonOnSaleItems` |
| 残留 3：管理员强制下架未通知卖家 | **已关闭**：`ItemApplicationService.adminOffShelf` 事务内发布 `SYSTEM` 消息，用例 `NotificationFlowTests.adminOffShelfNotifiesSeller` |
| 残留 4：`CATEGORY_IN_USE` 可构造但未专门断言 | 仍为未覆盖项（属 BE-MVP-02 范围，本任务未追加断言） |
| 残留 5/6：收藏列表失效商品呈现、可收藏范围 | 已裁决「保持现状」，未改动 |

---

## 9. 未覆盖 / 已知残留

1. **前端真实联调仍待执行**：截至本报告重跑时，**8080 无监听进程**（`GET /actuator/health` 返回 000，`netstat` 无 8080 LISTENING），因此本轮只有真实 MySQL 端到端用例，没有像 MVP-02 §9 那样的真实前端联调。按约定启动/重启 8080 需项目负责人授权；授权后以本分支产物启动即可与 FE-MVP-03 分支做真实主链联调（创建订单 → 接单 → 交付 → 收货 → 评价 → 消息/红点 → 管理台四区域）。
2. **`FlywayMigrationIT` / `IdentifierCollationIT` 不可运行**（无 Docker，见 §6）；它们不参与 `./mvnw -B -o test`，不产生 error（见 §4）。
3. `./mvnw clean` / `spring-boot:repackage` 在 8080 实例运行期间会失败（Windows 无法重命名被占用的 jar），与代码无关；本轮在 8080 空闲状态下完成验证，无需规避。`target/surefire-reports` 可能残留已删源码的历史报告，统计用例数一律以本轮新生成的 9 份报告为准（§4 已列出）。
4. 契约中订单的 `tradeMode` 只支持 `OFFLINE`；`SIMULATED_PAYMENT` 等值按 400 `VALIDATION_ERROR` 拒绝（用例 `unsupportedTradeModeRejected`），符合当前里程碑不做支付的边界。
5. 消息**无删除/归档**能力，`read-all` 无分页粒度；契约未要求。

---

## 10. 下一任务输入

- 可复用：`OrderTestSupport`（多用户 + 认证 + 发布上架 + 走完订单的脚手架，`RUN_ID` 隔离机制对**任何**真实库写用例都必要）、`NotificationPublisher`（事务内发事件、提交后落库的通用模式）、`LikePatterns`（关键字转义）、`PageResult` + `new Page<>(page + 1L, size)` 的分页约定。
- 前端对接要点：下单必须带 `clientRequestId`（UUID）才能拿到幂等保护；重放返回 200 而非 201，客户端应把两者都当成功并复用返回的订单 ID。
- 新增受保护接口无需改 `SecurityConfig`；只有契约标注 `security: []` 的读接口才加入 `PUBLIC_GET`，且必须精确到单段。
- 若后续要在契约里冻结 `bizType`（§7.2）或补评价版本冲突错误码（§7.3），需走 §4 变更流程：先总纲、再 `openapi.yaml`，并在同一 PR 内记录裁定。

---

## 11. 审查意见修复（2026-09-13）

负责人审查共 6 项，本分支修复第 2/3/4/6 项并各留回归用例；第 1 项（缺失的管理员 GUI）属前端范围，由 `frontend/docs/handoffs/FE-MVP-03.md` 记录；第 5 项（真实联调 + GUI 冒烟）受 8080 授权阻塞，见 §9.1。

修复后全量重跑：**60/60，BUILD SUCCESS，EXIT 0**（§4）。

### 11.1 通知失败隔离（审查意见 2）

原实现把 `@Transactional(propagation = REQUIRES_NEW)` 与 `try/catch` 放在监听器的同一个方法里。这里有个容易漏掉的事实：**`REQUIRES_NEW` 的提交发生在代理方法返回之后**，`try` 盖得住 `save()`，盖不住随后的事务提交/flush。而 Spring 的 `processCommit` 把 `triggerAfterCommit(status)` 包在 `try/finally` 中（源码注释：an exception thrown there propagated to callers but the transaction still considered as committed），且 `SimpleApplicationEventMulticaster.invokeListener` 只在设置了 `errorHandler` 时才吞掉监听器异常（Boot 默认不设）。两者叠加即负责人指出的路径：**业务事务已提交，异常却从 afterCommit 回到业务调用方，接口返回 500**。

改法是把新事务写入拆成独立 Bean：

- 新增 `notification/application/NotificationWriter.java`：只有 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 的 `write(NotificationMessage)`，异常直接抛，自己不做任何捕获。
- `NotificationPublisher.onMessage` 只保留 `@TransactionalEventListener(phase = AFTER_COMMIT)`，注入 `NotificationWriter`，捕获**整个代理调用**：

```java
try {
    notificationWriter.write(message);
} catch (RuntimeException ex) {
    log.warn("站内消息写入失败 type={} userId={} bizType={} bizId={}", ...);
}
```

提交阶段抛出的异常现在同样落在 catch 内，不再能改变业务调用方看到的提交结果。

**回归用例的强度如实说明**：`notificationWriteFailureIsIsolated` 用一条外键目标不存在的消息触发写入失败（`GenerationType.IDENTITY` 下违规在 `save()` 阶段就抛出），所以它证明的是**「单条消息写失败被隔离、同批其余消息照常落库」这一性质**，并非提交阶段路径的直接复现——提交阶段的异常已被结构性挪进 catch 覆盖范围，单进程测试里无法稳定构造。**它是一条性质回归兜底，不是对本次修复的证明**，评审请据此权衡。

### 11.2 接单的判定顺序（审查意见 3）

`confirm` 原顺序是「先判商品是否 `RESERVED`，再由 `order.confirm()` 校验卖家身份」。对一笔**已完成**的订单，非参与方调接单会先撞上「商品不处于预留」（409），而不是身份不符（403）——403 与 409 的差别本身就是信息，可用以探测订单进度。

修复：

- `Order.requireSeller` 由 `private` 改为 `public`；身份规则仍只有这一份实现，注释明确要求应用层不得自行判断买卖双方。
- `OrderApplicationService.confirm` 调整为 `requireActiveUser` → `requireOrderForUpdate` → **`order.requireSeller`** → `requireItemForUpdate` → 商品状态，并在代码里写明理由，避免被后续「顺手优化」回去。

用例 `confirmChecksIdentityBeforeItemState` 先把订单走完使商品变 `SOLD`，再断言三件事：陌生人查详情 403、陌生人接单 403 `ORDER_OPERATION_FORBIDDEN`、卖家接单 409 `ORDER_ILLEGAL_STATUS_TRANSITION`。

**判别力已实测**：临时把顺序改回「先商品后身份」重跑，该用例以 `Status expected:<403> but was:<409>` 失败；恢复修复后通过。缺陷真实存在，用例确实拦得住。

### 11.3 真并发用例（审查意见 4）

原并发场景是「第一个买家下完单、再让第二个买家请求」，属串行状态判定，测不出悲观锁。新增 `order/OrderConcurrencyTests`：两个请求**各占独立线程、各持独立事务**，用 `CountDownLatch` 等两个线程都就位后同点放行（先就位再放行是关键——否则先启动的请求可能已跑完，用例又退化成串行）。用例类不加 `@Transactional`，请求必须在各自线程真实提交，锁才有意义。

两条用例断言的是**不变量**而非时序：`{201, 409}` 与 `{201, 200}`，赢家恒为恰好一个；调度抖动不会让它随机失败，而**移除悲观锁会稳定失败**。

### 11.4 复现命令与 Docker IT（审查意见 6）

见 §4：普通 `./mvnw -B -o test` **不需要任何排除项**，也不产生 Docker 相关 error。两个 IT 类名以 `IT` 结尾，既不匹配 surefire 默认包含模式，仓库也无 failsafe 插件与自定义 surefire 配置，因此不会被加载。若在 `target/surefire-reports` 里看到它们的报告，那是历史遗留文件（本轮已连同已删除的 `AnonymousProbeTests` 一并清理），与本次执行无关。
