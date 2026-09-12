# BE-MVP-02 交接报告

## 1. 基本信息

| 项 | 值 |
| --- | --- |
| 任务 ID | BE-MVP-02 |
| 分支 | `feat/BE-MVP-02-item-favorite` |
| 基线 | `origin/main` = `91d2cf9`（BE-MVP-01 合并后；契约 API-01 冻结版 / Flyway V6） |
| PR | [#6](https://github.com/tednved/graduation-backend/pull/6)（Draft） |
| 状态 | **IN_PROGRESS** —— 实现与真实库验证均已完成（真实 MySQL 36/36，见 §5/§8）；真实前后端联调 22/22（见 §9）。PR 仍为 Draft，等待负责人验收；不得由 Agent 置 DONE |
| 契约源 | `backend/openapi.yaml`（本 PR 未修改） |

本任务闭合「发布 → 编辑 → 上下架 → 详情 → 搜索 → 收藏」商品闭环，含管理端强制下架。

---

## 2. 实现结果

| 范围 | 落实 |
| --- | --- |
| 创建草稿 | `POST /items`：要求 ACTIVE + 认证 `APPROVED`（否则 403 `USER_CERTIFICATION_REQUIRED`）；请求不接受校区字段，服务端按 `code=MAIN` 绑定唯一校区；金额用字符串接收后经 `Money` 转 `BigDecimal`（两位小数、上限 `99999999.99`）；图片 1~9 张、必须本人 `ITEM_IMAGE` 且 `UPLOADED`；`sortNo` 由服务端按入参顺序生成 1~9 |
| 编辑 | `PUT /items/{id}`：省略字段保留原值（见 §6 契约矛盾），显式 `null` 仅 `originalPrice` 有意义（清除），其余字段显式 `null` 400；`version` 必填，不匹配 409 `ITEM_NOT_EDITABLE`；仅 `DRAFT`/`OFF_SHELF` 且未 `adminLock` 可改 |
| 上下架/删除 | 上架复核分类与校区仍启用、至少一张图片；下架仅 `ON_SALE`；删除只改状态为 `DELETED`（存在进行中订单时 409） |
| 详情 | `GET /items/{id}` 匿名可读；`favorited` 匿名返回 `null`（与「未收藏」区分）；`isOwner`/`canBuy`/`allowedActions` 按查看者身份计算；`DRAFT`/`DELETED` 仅本人与管理员可见，他人一律 404 |
| 搜索 | `GET /items` 只返回 `ON_SALE`；关键词（`%`/`_` 转义）、分类（传一级自动展开启用子分类）、成色、价格区间、四种排序（枚举白名单）；走 MyBatis-Plus 物理分页，排序值用 `<choose>` 固定字面量 |
| 我的发布 | `GET /users/me/items` 含全部状态，可按 `status` 筛选 |
| 收藏 | 收藏/取消幂等（204）；不能收藏自己商品（409 `ITEM_SELF_OPERATION`）；`DRAFT`/`DELETED`/不存在一律 404；`favorite_count` 只在真的增删后经原子 SQL 调整，减法用 `GREATEST(x-1,0)` |
| 管理端 | `POST /admin/items/{id}/off-shelf`：应用层 `UserService.requireAdmin` 判权（403 `AUTH_FORBIDDEN`），置 `OFF_SHELF` + `adminLock=true` 并记录原因，写 `ITEM_ADMIN_OFF_SHELF` 审计；`DELETED` 商品 404 |

并发与一致性取舍：

- 所有会改 `version` 的写路径先 `findByIdForUpdate` 悲观锁定商品行，再显式比对 `version`：乐观锁不会在提交时才失败，避免把「版本过期」表现成 500。
- 收藏写入先锁用户行（`requireActiveUserForUpdate`，与认证模块同一模式），使「查有没有 → 插入」不会两个事务都查不到而各插一行；`uk_favorites_user_item` 仍是最后一道防线。
- 浏览量自增在 `REQUIRES_NEW` 独立事务中执行且吞掉异常，只留告警日志——契约要求「自增失败不影响详情返回」。
- `favorite_count`/`view_count` 在实体上标 `insertable=false, updatable=false`，只经 `@Modifying` 原子 SQL 变更，避免刷实体时把内存旧计数写回覆盖并发增量。

---

## 3. 交付物与修改文件

新增：

- `src/main/java/com/graduation/backend/item/**`（`domain`/`application`/`api`/`query`/`api/dto`，含 `Item`、`ItemStatus`、`ItemCondition`、`ItemAction`、`ItemSort`、`Money`、`ItemPermissionService`、`ItemRepository`、`ItemImage*`、`ItemViewerResolver`、`ItemViewCounter`、`ItemQueryService`、`ItemApplicationService`、`ItemController`、`ItemSearchController` 及 6 个 DTO）
- `src/main/java/com/graduation/backend/admin/api/AdminItemController.java`
- `src/main/java/com/graduation/backend/favorite/**`（实体、仓库、`FavoriteApplicationService`、`FavoriteQueryService`、`FavoriteController`、`FavoriteStatusResponse`、`FavoriteQueryMapper`）
- `src/main/resources/mapper/item/ItemQueryMapper.xml`、`src/main/resources/mapper/favorite/FavoriteQueryMapper.xml`
- `src/test/java/com/graduation/backend/item/{ItemApiFlowTests,ItemTestSupport}.java`、`src/test/java/com/graduation/backend/favorite/FavoriteFlowTests.java`

修改：

| 文件 | 改动 | 说明 |
| --- | --- | --- |
| `common/config/SecurityConfig.java` | `PUBLIC_GET` 增加 `/api/v1/items` 与 `/api/v1/items/*` | **本任务唯一被允许的跨模块行为改动**，契约把商品搜索与详情标为 `security: []`；已按单段模式精确放行 |
| `file/application/FileService.java` | 新增 `requireBindableItemImage`/`bindItemImage`/`unbindItemImage` | 纯新增方法，未改既有方法体 |
| `file/domain/FileObject.java` | 新增 `markDeleted()` | **超出你给出的写入范围**（该范围只列到 `FileService`）。为了让「被替换的图片解绑进入待清理状态」有领域入口，必须有一个方法把状态置为 `DELETED`；纯新增、无调用方，未改动既有行为。请审查时确认是否接受 |

未改动：`openapi.yaml`、`pom.xml`、`src/main/resources/db/migration/**`（V1～V6）、`common/**` 既有行为、其他业务模块。表 `items`/`item_images`（V2）与 `favorites`（V3）已存在，**未新增迁移**。

---

## 4. 验证证据

| 命令 | 结果 |
| --- | --- |
| `./mvnw -B -DskipTests compile` | **EXIT 0**，`BUILD SUCCESS`（122 个主源文件；`target/classes` 内含新增类与两个 mapper XML） |
| `./mvnw -B test-compile` | **EXIT 0**，`BUILD SUCCESS`（11 个测试源文件；仅 `CoreApiFlowTests` 既有 deprecation 提示） |

`compile`/`test-compile` 不连库；按分工**未运行** `test`/`verify`（真实 MySQL 凭据由主线程持有）。主线程执行方式：

```bash
DB_IT_USERNAME=<user> DB_IT_PASSWORD=<pwd> ./mvnw -B test -Dtest=ItemApiFlowTests+FavoriteFlowTests
```

安全边界自查（`PUBLIC_GET` 单段模式）：用 Spring 7 的 `AntPathMatcher` 与 `PathPatternParser` 双实现实测

```
/api/v1/items/*  vs  /api/v1/items/12                 匹配
/api/v1/items/*  vs  /api/v1/items/12/favorite-status  不匹配
/api/v1/items/*  vs  /api/v1/items/12/favorite          不匹配
```

即 `GET /api/v1/items/{itemId}/favorite-status` 仍然要求登录，`FavoriteFlowTests` 里对此有 401 断言兜底。

---

## 5. 测试覆盖（已在真实库执行，36/36 全绿）

- `ItemApiFlowTests`（15 个用例）：创建/上架/搜索/详情主链路（含 MAIN 校区绑定、`sortNo`、`coverImageUrl`、`allowedActions`）；匿名 vs 买家 vs 卖家的详情个性化；浏览量自增落库；认证前置（401 / `USER_CERTIFICATION_REQUIRED` / `USER_DISABLED`）；图片数量、重复、他人文件、类型不符、文件不存在、`campusId` 契约外字段；分类 404/一级/停用，以及分类停用后不能上架；金额与文本边界；编辑的「省略保留 / 显式 null / 版本冲突 / 图片替换解绑」；状态机 409；删除只改状态；草稿可见性；非卖家 403 与不可见 404；管理端强制下架 + 审计 + 锁定后不可改不可重上架；我的发布与搜索过滤。
- `FavoriteFlowTests`（5 个用例）：跨用户闭环（不能收藏自己 409、收藏他人出现在列表、取消后消失、计数幂等且不为负）；收藏列表倒序与分页；草稿/已删除/不存在 404；收藏类接口匿名 401（含 `favorite-status` 的安全回归断言）；禁用账号 403。
- 测试用 `code` 派生 openid（测试 profile 的 `app.wechat.mock-openid` 为空），因此跨用户场景是真实多用户，不是单用户近似。

---

## 6. 已知残留与未覆盖

1. **契约自相矛盾（`PUT /items/{id}`）——已按宽容语义实现，请负责人裁决**：
   - `openapi.yaml:874`（路径级 description）：「本接口为全量替换，必须携带当前 `version`；版本不匹配返回 `ITEM_NOT_EDITABLE`。」
   - `openapi.yaml:3068`（`UpdateItemRequest` schema description）：「只允许修改 `DRAFT` 或 `OFF_SHELF` 商品；**字段省略表示保留原值**」。
   - 本实现取后者：缺字段 = 保留原值；显式 `null` 只在 `originalPrice` 上表示清除，其余字段显式 `null` 按 400 拒绝（避免用 `null` 绕过必填）；`version` 必填且不匹配 409 `ITEM_NOT_EDITABLE`。测试按此语义编写。
2. **`GET /admin/items`（`listAdminItems`）未实现**：契约 `openapi.yaml:2000-2005`，`x-owner-task: BE-10`，按分工属 BE-MVP-03，本里程碑不落。
3. **管理端强制下架未创建卖家通知**：契约要求通知卖家，通知能力属 BE-09，未实现；审计已写。
4. **`CATEGORY_IN_USE` 现在可构造**（BE-MVP-01 的残留）：`items` 已有真实商品，分类停用时的在售校验可被真实数据覆盖；本任务的测试未专门断言该路径。
5. **收藏列表的失效商品呈现——已裁决为「保持现状」**：当前实现会返回 `OFF_SHELF`/`SOLD`/`DELETED` 的收藏项并带上 `status`，由前端呈现「已失效」。`项目全局规划.md:1010` 明确该页的两条交互是「即时移除、失效状态」，因此接口层不做过滤是正确的，不需要改 `FavoriteQueryMapper`。
6. **可收藏范围——已裁决为「保持现状」**：`requireFavoritableItem` 按「公开可见」判定（`OFF_SHELF`/`RESERVED`/`SOLD` 可收藏），只有 `DRAFT`/`DELETED` 是 404。契约 `openapi.yaml:1106` 的原文是「商品必须存在且非 `DRAFT`/`DELETED`」，与实现一致，不需要收窄到「仅在售」。
7. **真实库测试已执行（主线程，2026-09-12）**：`./mvnw -B verify` 在真实 MySQL 上 36/36 全绿（`BackendApplicationTests` 2 + `CoreApiFlowTests` 14 + `ItemApiFlowTests` 15 + `FavoriteFlowTests` 5），V1～V6 从空 schema 重放 + `ddl-auto=validate` 通过。首次执行暴露了 4 处缺陷，均已修复，见 §8。
8. **`FileObject.markDeleted()` 超出写入范围——已审查确认接受**（见 §3）：`FileStatus` 只有 `UPLOADED`/`BOUND`/`DELETED` 三态，`DELETED` 就是契约「解绑进入待清理状态」的唯一落点；若在 `ItemApplicationService` 里直接赋值又会违反「状态变更只走领域方法」。改动 10 行、幂等、不影响其他调用方，接受。

---

## 7. 下一任务输入

- 可复用：`ItemPermissionService`（可见性/归属/可执行动作单一来源）、`Money`（两位小数金额解析）、`ItemViewerResolver`（匿名可读接口的查看者解析）、`ItemQueryService.assemble`（写路径详情回显，不重复查库、不自增浏览量）、`ItemQueryMapper.findSellableCategoryIds`（分类展开）、`ItemTestSupport`（多用户 + 认证 + 图片上传的测试脚手架）。
- 订单里程碑注意：`Item` 已提供 `reserve`/`release`/`markSold` 领域方法，`ItemRepository.countInProgressOrders` 已用于删除前置；下单校验请复用 `ItemPermissionService.canBuy`，保持与详情 `canBuy` 一致。
- 通知里程碑（BE-09）：管理员强制下架处需要补卖家通知，落点见 `ItemApplicationService.adminOffShelf`。
- 新增受保护接口无需改 `SecurityConfig`；只有契约标注 `security: []` 的读接口才加入 `PUBLIC_GET`，且**必须精确到单段**（`/api/v1/items/*` 而不是 `/api/v1/items/**`，否则 `favorite-status` 会被匿名放行）。

---

## 8. 主线程真实库复核与修复（2026-09-12，提交 `47d7888`）

真实库首跑 `20 errors + 4 failures`，逐个定位后全部修复，现 36/36 全绿。两个是产品缺陷，三个是测试写法缺陷：

**产品缺陷（都会让客户端无法完成第二次写操作）**

1. **写路径回显的是刷盘前的旧 `version`**：`@Version` 由 Hibernate 在 flush 时自增，而提交发生在方法返回之后，`itemQueryService.assemble` 读到的是改动前的值。契约要求编辑必须携带最新 `version`，客户端照做必然吃到 `ITEM_NOT_EDITABLE`（表现为「下架成功后再编辑 409」）。已在 `update`/`publish`/`offShelf`/`adminOffShelf` 回显前显式 flush，收敛成 `assembleAfterFlush`。`create` 不受影响（IDENTITY 插入时已拿到 `version=0`）。
2. **`item_images.sort_no` 映射缺 JDBC 类型**：库列是 `TINYINT UNSIGNED`，Hibernate 不显式声明时期望 `INTEGER`，`ddl-auto=validate` 直接启动失败（`BackendApplicationTests` 也起不来）。补 `@JdbcTypeCode(SqlTypes.TINYINT)`。

**测试缺陷（断言写法，非实现问题）**

3. `ItemTestSupport.userIdOf` 读 `$.data.userId`，而登录响应把用户摘要嵌在 `user` 里，正确路径是 `$.data.user.id`；该错误让 20 个用例全部在脚手架阶段报错。
4. Spring 7 的 `JsonPathExpectationsHelper` 对不定路径（含 `[?(@...)]` 过滤器）不再匹配 `.value(List.of(x))`（实测返回 `null`，同内容的 `JsonPath.read` 正常）。三处改为语义等价的 `.value(contains(x))`——`contains` 要求「恰好一个匹配元素」，与 `List.of(x)` 强度相同，未放宽断言。

**复核过的既有约定（无需改动）**

- 计数只走原子 SQL：`ItemRepository` 的 `incrementViewCount`/`incrementFavoriteCount`/`decrementFavoriteCount` 均为 `@Modifying` 原生 SQL，实体上 `viewCount`/`favoriteCount` 标了 `insertable=false, updatable=false`，不会在刷实体时覆盖并发增量。
- 收藏自己商品是 409 `ITEM_SELF_OPERATION`，已固化断言。
- `PUBLIC_GET` 精确到单段（`/api/v1/items`、`/api/v1/items/*`），`favorite-status` 保持需要登录。
- 全仓无 `@PreAuthorize`/`hasRole`，管理端越权走应用层 `BusinessException(AUTH_FORBIDDEN)`，与既有约定一致。
- 分支全量 diff 无口令/密钥/`session_key`；测试与文档只出现 `DB_IT_USERNAME=<user>` 这类占位符。

**遗留**：`./mvnw -B verify` 在负责人保持 8080 实例运行时会在 `spring-boot:repackage` 失败（Windows 无法重命名被占用的 jar），与代码无关；跳过 repackage 后 `BUILD SUCCESS`。真实前后端联调（`item-chain.js`）需要先用新 jar 重启 8080。

---

## 9. 真实前后端联调（2026-09-12）

联调脚本 `item-chain.js` 放在仓库外（`D:\tmp-integration`），不是交付物；它 `require` 前端真实模块
（`services/*-api.js`、`store/session-store.js`、`constants/enums.js`，以及发布页的
`pages/publish/item-form.js`），用 `wx-real.js` 顶掉 `global.wx`，对 `127.0.0.1:8080` 打真实 HTTP。
先用新 jar 重启 8080（已获负责人授权），随后：

```
IT_RUN=mvp02 node item-chain.js
```

结果 **22 PASS / 0 FAIL / 1 SKIP，EXIT=0**。

覆盖：登录 → 认证前置（未通过时自行提交并以管理员身份通过）→ 取启用的二级分类 → 上传 `ITEM_IMAGE` →
创建草稿（MAIN 校区绑定、图片绑定、金额两位小数）→ 草稿本人可见且公开搜索不可见 → 上架 →
**上架响应回显的 version 与详情回读一致**（刷盘回显回归）→ 下架 →
**用下架响应的 version 立刻编辑成功且 version 增长**（§8 缺陷 1 的回归）→ 重新上架 →
关键词搜索命中 → 二级分类筛选命中 → **只传一级分类展开到启用子类并命中** → 匿名详情
`isOwner=false`/`favorited=null` → 浏览量自增 → 收藏自己商品 409 `ITEM_SELF_OPERATION` → 我的发布 →
在售不可直接删除、下架后删除 204 → 删除后搜索不再命中。

唯一 SKIP 是跨用户收藏：演示环境把 Mock 登录钉在单一管理员账号，两次登录是同一用户；脚本还会在库里
找「其他卖家的在售商品」作为替代目标，本次库里没有（商品全属该账号），故如实 SKIP 而非伪造通过。
该场景由真实 MySQL 用例 `FavoriteFlowTests` 覆盖（测试 profile 的 `mock-openid` 为空，是真实多用户）。

联调中出现的 3 次失败**全部是脚本自身缺陷，不是产品缺陷**，且每次都被后端按契约挡下，反过来印证了契约校验有效：

1. 上传步骤返回的是展示用字符串而不是 `fileId`，`imageFileIds` 传了非 ID 值 → 400 `VALIDATION_ERROR`。
2. 用「只带 `title`+`version` 的半截请求体」调 `PUT`：前端 `buildWriteBody` 会把缺失字段补齐成
   `price: null` 与 `imageFileIds: []`，两者都被 `UpdateItemRequest` 判为非法 → 400。真实页面从不这样调用
   （`publish.js` 先 `validateItemForm` 再 `buildPayload`，始终提交完整表单）；改走同一条真实路径后通过。
3. 在 `ON_SALE` 状态直接编辑、直接删除 → 409 `ITEM_NOT_EDITABLE`，与契约「只有 `DRAFT`/`OFF_SHELF`
   可编辑可删除」一致，属脚本用错状态。

**待负责人裁决的残留（属前端）**：`services/item-api.js` 的 `buildWriteBody` 固定生成
`title`/`description`/`price`/`condition`/`categoryId`/`imageFileIds` 全部键，因此经前端服务层
**无法表达契约允许的「省略字段即保留原值」**——缺省会被补齐成 `null` 或空数组并被后端拒绝。
真实 UI 始终提交完整表单，当前不可见；是否让服务层支持真正的局部更新，请负责人定。

**顺带修复的仓库卫生**：`app.file.storage-root` 默认 `./var/media`，`FileProperties` 已注明
「不纳入版本控制」，但 `.gitignore` 没有对应规则，联调上传的图片成了未跟踪文件；已补 `/var/`。
