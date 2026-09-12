# BE-MVP-02 交接报告

## 1. 基本信息

| 项 | 值 |
| --- | --- |
| 任务 ID | BE-MVP-02 |
| 分支 | `feat/BE-MVP-02-item-favorite` |
| 基线 | `origin/main` = `91d2cf9`（BE-MVP-01 合并后；契约 API-01 冻结版 / Flyway V6） |
| PR | [#6](https://github.com/tednved/graduation-backend/pull/6)（Draft） |
| 状态 | **IN_PROGRESS** —— 实现完成、`compile` 与 `test-compile` 均 EXIT 0；**测试尚未在真实 MySQL 上执行**（真实库凭据由主线程持有），故本报告不声称测试通过 |
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

## 5. 测试覆盖（已编写，待真实库执行）

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
5. **收藏列表的失效商品呈现**：当前实现会返回 `OFF_SHELF`/`SOLD`/`DELETED` 的收藏项并带上 `status`，由前端呈现「已失效」（与 §10「即时移除、失效状态」一致）。若负责人希望接口层过滤失效商品，需要改 `FavoriteQueryMapper`；测试未固化这一呈现方式，只固化了「收藏关系与计数」。
6. **可收藏范围**：`requireFavoritableItem` 按「公开可见」判定（即 `OFF_SHELF`/`RESERVED`/`SOLD` 也可收藏，与详情可见性一致），只有 `DRAFT`/`DELETED` 是 404。若希望「仅在售可收藏」，改一处判定即可。
7. **未执行真实库测试**：本报告只包含编译证据，测试结论待主线程执行后回填。
8. **`FileObject.markDeleted()` 超出写入范围**（见 §3），需审查确认。

---

## 7. 下一任务输入

- 可复用：`ItemPermissionService`（可见性/归属/可执行动作单一来源）、`Money`（两位小数金额解析）、`ItemViewerResolver`（匿名可读接口的查看者解析）、`ItemQueryService.assemble`（写路径详情回显，不重复查库、不自增浏览量）、`ItemQueryMapper.findSellableCategoryIds`（分类展开）、`ItemTestSupport`（多用户 + 认证 + 图片上传的测试脚手架）。
- 订单里程碑注意：`Item` 已提供 `reserve`/`release`/`markSold` 领域方法，`ItemRepository.countInProgressOrders` 已用于删除前置；下单校验请复用 `ItemPermissionService.canBuy`，保持与详情 `canBuy` 一致。
- 通知里程碑（BE-09）：管理员强制下架处需要补卖家通知，落点见 `ItemApplicationService.adminOffShelf`。
- 新增受保护接口无需改 `SecurityConfig`；只有契约标注 `security: []` 的读接口才加入 `PUBLIC_GET`，且**必须精确到单段**（`/api/v1/items/*` 而不是 `/api/v1/items/**`，否则 `favorite-status` 会被匿名放行）。
