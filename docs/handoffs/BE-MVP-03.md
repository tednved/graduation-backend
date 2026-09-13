# BE-MVP-03 交接报告

## 1. 基本信息

| 项 | 值 |
| --- | --- |
| 任务 ID | BE-MVP-03 |
| 分支 | `feat/BE-MVP-03-trade` |
| 基线 | `origin/main` = `ce30414`（BE-MVP-02 合并后的日志提交；契约 API-01 冻结版 / Flyway V6） |
| PR | [#7](https://github.com/tednved/graduation-backend/pull/7)（Draft） |
| 状态 | **REVIEW** —— 待项目负责人审查与合并；按约定 Agent 不登记 `DONE` |
| 契约源 | `backend/openapi.yaml`（**本 PR 未修改契约**，未新增迁移） |

本任务闭合「下单 → 接单/拒单 → 交付 → 收货」订单闭环，并补齐评价、站内消息与未读红点、必要管理端（用户分页与禁用/恢复、商品分页、审计查询）。

---

## 2. 实现结果

| 范围 | 落实 |
| --- | --- |
| 下单 | `POST /orders`：要求 ACTIVE + 认证 `APPROVED`（否则 403 `USER_CERTIFICATION_REQUIRED`）；不能购买自己商品（409 `ITEM_SELF_PURCHASE`）；金额与商品信息取快照落 `order_snapshots`，事后改价不影响已生成订单；商品转 `RESERVED` 与订单创建同事务 |
| 幂等 | 先锁买家行（`requireActiveUserForUpdate`）再按 `(buyer_id, client_request_id)` 查已有订单：同键同商品返回原订单 **200**（不产生第二笔、不新增事件、不重复预留），同键换商品 409 `ORDER_DUPLICATE_REQUEST`；首次创建 **201** |
| 状态机 | `PENDING_CONFIRMATION` →（卖家）`CONFIRMED` →（卖家）`PENDING_RECEIPT` →（买家）`COMPLETED`；`REJECTED`/`CANCELLED` 为终态。身份不符 403 `ORDER_OPERATION_FORBIDDEN`，状态不符 409 `ORDER_ILLEGAL_STATUS_TRANSITION` |
| 事件时间线 | 每次流转追加一条 `order_events`（`fromStatus` 首条为 `null`、`toStatus`、`action`、`operator`、`remark`），详情按时间正序返回 |
| 商品状态联动 | 接单/交付期间保持 `RESERVED`；收货转 `SOLD`；拒单与取消释放回 `ON_SALE`。商品状态与订单状态永远同事务提交，不出现「订单已完成但商品仍可下单」的中间态 |
| 我的订单 | `GET /users/me/orders`：`side=BUY` 对端为卖家、`side=SELL` 对端为买家，可按 `status` 筛选；MyBatis-Plus 物理分页 |
| 评价 | `POST /reviews`：仅订单双方且订单 `COMPLETED` 可评（否则 409 `REVIEW_NOT_ALLOWED`）；`revieweeId` 由服务端按订单对端推导，请求体无此字段；重复评价 409 `REVIEW_ALREADY_EXISTS`；`rating` 1~5 由 Bean Validation 拦为 400；可选 `version` 校验 |
| 评价读取 | `GET /orders/{id}/review-eligibility`（参与方；非参与方 403）、`GET /users/{id}/reviews`（公开，可 `rating` 筛选）、`GET /users/{id}/credit`（公开，平均分/评价数/1~5 分布） |
| 信用汇总 | 落库值由数据库按 `VISIBLE` 评价重算，写路径**先锁订单行**再聚合——订单是双方评价唯一的公共对象，避免两次互评互相覆盖；重算幂等，平均分两位小数，无评价时 `0.00` |
| 站内消息 | 订单状态变化（下单/接单/拒单/取消/交付/收货）与管理员下架均生成消息；业务事务内发布 `NotificationMessage`，`@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW` 写入，写入失败只告警不回滚业务 |
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
- `notification/**`：`domain`（`Notification`、`NotificationType`、`NotificationRepository`）、`application`（`NotificationPublisher`、`NotificationMessage`、`NotificationApplicationService`、`NotificationQueryService`）、`api`（`NotificationController` 及 3 个 DTO）、`query`（`NotificationQueryMapper`、`NotificationRow`）
- `admin/**`：`AdminUserController`、`AdminAuditLogController`、`AdminUserService`、`AdminItemQueryService`、`AuditLogQueryService`、三个 query mapper 与 row、三个响应 DTO
- `common/web/LikePatterns.java`（关键字 `%`/`_` 转义，与 BE-MVP-02 的搜索同源）
- `src/main/resources/mapper/{order,review,notification,admin}/*.xml`（6 个）
- `src/test/java/com/graduation/backend/{order/OrderApiFlowTests,order/OrderTestSupport,review/ReviewFlowTests,notification/NotificationFlowTests,admin/AdminApiFlowTests}.java`

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

未改动：`pom.xml`、`src/main/resources/db/migration/**`（V1～V6 只读未动，**无新增迁移**）、`openapi.yaml`、`frontend` 侧任何文件。表 `orders`/`order_events`/`order_snapshots`/`reviews`/`notifications`/`audit_logs` 均已在 V3/V4 建好。

---

## 4. 验证证据

| 命令 | 结果 |
| --- | --- |
| `DB_IT_USERNAME=<user> DB_IT_PASSWORD=<pwd> ./mvnw -B -o test` | **Tests run: 56, Failures: 0, Errors: 0, Skipped: 0**，`BUILD SUCCESS`，EXIT 0 |
| `powershell -File scripts/validate-openapi.ps1` | **EXIT 0**（51 个操作 / 15 个枚举 / 25 个错误码全部通过结构与一致性校验） |
| `git diff --check origin/main...HEAD` | 无输出，EXIT 0 |

测试总数由 MVP-02 的 36 增至 **56**（新增 20 个用例）。真实 MySQL 用例在 `RealMySqlTestConfiguration` 下**每次上下文启动都从空 schema 重放 V1～V6 再 `ddl-auto=validate`**，因此本轮 56/56 同时覆盖「迁移可重放 + 实体映射与库结构一致」这一验收项。

安全自查：分支全量 diff 无口令/密钥/`session_key`/完整手机号学号；`tednved`/`0721` 仅出现在既有、负责人授权的 `src/main/resources/application.properties`；文档与测试只出现 `DB_IT_USERNAME=<user>` 这类占位符。

---

## 5. 测试覆盖（真实 MySQL，56/56 全绿）

本轮新增 20 个用例，全部端到端走 HTTP：

- `OrderApiFlowTests`（9）：`fullTradeChain`（下单 → 商品 `RESERVED`、买家 `allowedActions=[CANCEL]`、卖家 `[CONFIRM,REJECT]`；接单 → `[CANCEL,DELIVER]`；交付 → 空；收货 → `COMPLETED` + 商品 `SOLD` + 4 条事件 + 首条 `fromStatus` 为 `null`）；`idempotentCreateReplaysSameOrder`（201 → 200，同 ID/订单号/事件数，`orders` 行数仍为 1；换商品 409 `ORDER_DUPLICATE_REQUEST`）；`selfPurchaseAndCertificationRejected`；`unavailableItemRejected`（`ITEM_CONCURRENTLY_RESERVED` / `ITEM_NOT_AVAILABLE` / 404）；`unsupportedTradeModeRejected`（400 且商品仍在售）；`onlyParticipantsCanAccessOrder`（非参与方 403、管理员 200、匿名 401）；`illegalTransitionsRejected`（403 与 409 分流、商品保持 `RESERVED`）；`rejectAndCancelReleaseItem`（拒单与取消都释放商品，第三人可继续下单）；`myOrdersSeparatesSides`。
- `ReviewFlowTests`（3）：`bothSidesReviewAndCreditAggregated`（被评价人按对端推导、重复 409、双方信用各自聚合、分布补零、公开列表匿名可读且可筛选）；`reviewRequiresCompletedOrderAndParticipation`；`reviewVersionConflict`（版本不一致 409，一致 201，未填内容存 `null` 而非空串）。
- `NotificationFlowTests`（4）：`orderLifecycleGeneratesNotifications`（下单通知卖家、接单通知买家、未读数互不干扰、标记已读幂等、他人的消息 404、匿名 401）；`markAllReadCountsOnlyChanges`；`adminOffShelfNotifiesSeller`（`SYSTEM` + `bizType=ITEM` + 内容含原因与标题）；`disabledUserLosesAccess`（旧令牌 403、刷新令牌全撤销、禁用后不能重新登录、恢复后可登录）。
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

`FlywayMigrationIT` / `IdentifierCollationIT` 仍依赖 Testcontainers，本机无 Docker 环境，**维持现状不可运行**（与 MVP-02 相同，非本任务引入）。V1～V6 重放 + `validate` 的覆盖由 `RealMySqlTestConfiguration` 提供，见 §4。

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

1. **前端真实联调未执行**：8080 上运行的是 BE-MVP-02 的 jar，不含本任务的接口；重启需要项目负责人授权，因此本轮只有真实 MySQL 端到端用例，没有像 MVP-02 §9 那样的真实前端联调。合并后如需联调，请授权重启 8080。
2. **`FlywayMigrationIT` / `IdentifierCollationIT` 不可运行**（无 Docker，见 §6），非本任务引入。
3. `./mvnw clean` / `spring-boot:repackage` 在 8080 实例运行期间仍会失败（Windows 无法重命名被占用的 jar），与代码无关；本轮因此在 8080 运行期间使用增量 `-o test`。另 `target/surefire-reports` 可能残留已删源码的报告，统计用例数应以本轮新生成的报告为准。
4. 契约中订单的 `tradeMode` 只支持 `OFFLINE`；`SIMULATED_PAYMENT` 等值按 400 `VALIDATION_ERROR` 拒绝（用例 `unsupportedTradeModeRejected`），符合当前里程碑不做支付的边界。
5. 消息**无删除/归档**能力，`read-all` 无分页粒度；契约未要求。

---

## 10. 下一任务输入

- 可复用：`OrderTestSupport`（多用户 + 认证 + 发布上架 + 走完订单的脚手架，`RUN_ID` 隔离机制对**任何**真实库写用例都必要）、`NotificationPublisher`（事务内发事件、提交后落库的通用模式）、`LikePatterns`（关键字转义）、`PageResult` + `new Page<>(page + 1L, size)` 的分页约定。
- 前端对接要点：下单必须带 `clientRequestId`（UUID）才能拿到幂等保护；重放返回 200 而非 201，客户端应把两者都当成功并复用返回的订单 ID。
- 新增受保护接口无需改 `SecurityConfig`；只有契约标注 `security: []` 的读接口才加入 `PUBLIC_GET`，且必须精确到单段。
- 若后续要在契约里冻结 `bizType`（§7.2）或补评价版本冲突错误码（§7.3），需走 §4 变更流程：先总纲、再 `openapi.yaml`，并在同一 PR 内记录裁定。
