# API 契约决策表（API-01）

本文件是 `backend/openapi.yaml` 的配套说明，固定表示规则和快速 MVP 产品决定。

- 契约唯一源文件：`backend/openapi.yaml`（OpenAPI 3.1）。
- 校验入口：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-openapi.ps1`。
- 本文件中的「已冻结」表示 API-01 依 `项目全局规划.md` §7.1.1 与 §8 直接确定，前后端实现必须一致。

---

## 1. 已冻结的表示规则

### 1.1 通用信封与内容类型

| 项 | 冻结值 | 依据 |
| --- | --- | --- |
| 基础路径 | `servers: [{ url: /api/v1 }]`，`paths` 不重复该前缀 | §7.1 |
| 成功信封 | `{ code: "OK", message: "success", data, requestId, timestamp }` | §7.1 |
| 失败信封 | `{ code, message, data: null, requestId, timestamp }` | §7.1.1 |
| 请求与响应体 | `application/json; charset=utf-8` | §7.1 |
| 字段命名 | `lowerCamelCase` | §7.1 |

`code` 在成功响应中是常量 `OK`，业务差异一律由 HTTP 状态码与 `data` 表达，不用 `code` 承载业务码。

### 1.2 标量表示

| 项 | 冻结值 | 说明 |
| --- | --- | --- |
| ID | 十进制字符串，`^(0\|[1-9][0-9]*)$` | 含路径参数、请求体与响应；避免微信 JavaScript 丢失 BIGINT 精度 |
| 普通计数 | 非负 JSON 整数 | `totalElements`、`reviewCount`、`favoriteCount` 等非 ID 数值 |
| 金额 | 两位小数字符串，如 `"10.00"` | 服务端转 `BigDecimal`，不使用浮点 |
| 评分汇总 | 两位小数字符串 | `averageRating` |
| 评分原值 | 1～5 整数 | `rating` |
| 时间 | UTC ISO 8601 毫秒，如 `2026-09-10T04:50:00.000Z` | 固定三位毫秒并以 `Z` 结尾 |
| 枚举 | 直接使用 §4 的字符串值 | 不使用 ordinal，不接受别名或大小写变体 |

依据：§7.1.1；§5.1（金额 `DECIMAL(10,2)`、时间 `DATETIME(3)`、枚举 `VARCHAR(32)`）。

### 1.3 分页与排序

| 项 | 冻结值 |
| --- | --- |
| `page` | 从 0 开始，最小 0，默认 0 |
| `size` | 1～100，默认 20 |
| 响应分页元数据 | `page`、`size`、`totalElements`、`totalPages`、`hasNext` |
| 列表字段名 | 统一为 `data.items` |
| 商品排序 | 白名单 `NEWEST`、`PRICE_ASC`、`PRICE_DESC`、`POPULAR` |

依据：§7.1（`page` 从 0 开始、`size` 默认 20 最大 100、排序白名单不接受任意列名）；§8.6。

### 1.4 `PATCH /users/me` 的省略与 null

| 字段 | 省略 | 显式 `null` |
| --- | --- | --- |
| `nickname` | 保留原值 | **非法**（`VALIDATION_ERROR`） |
| `phone` | 保留原值 | 清除手机号 |
| `avatarFileId` | 保留原值 | 清除头像 |

请求中出现 `role`、`status`、`openid` 或任何评分字段一律非法。昵称在服务端清理首尾空格后保存。
依据：§7.1.1、§8.2。

### 1.5 头像绑定

头像**只接受文件 ID**，不接受客户端提交的任意 URL，与商品图片同一策略。

- 请求字段：`PATCH /users/me` 的 `avatarFileId`。
- 服务端校验：文件属于当前用户、`bizType` 为 `AVATAR`、状态 `UPLOADED`。
- 响应字段：`avatarUrl`（绑定后写入 `users.avatar_url`，读取时返回）。

依据：§7.1.1 要求 API-01 明确头像的「空值与归属策略」；§8.5 禁止前端自行提交最终 URL。

### 1.6 令牌与登录

`TokenResponse` 字段名已冻结：

| 字段 | 类型 | 冻结值 / 说明 |
| --- | --- | --- |
| `accessToken` | string | 15 分钟有效的 Access Token |
| `refreshToken` | string | 新签发的刷新令牌明文，**只在本响应出现一次**，服务端只存 SHA-256 哈希 |
| `tokenType` | string | 常量 `Bearer` |
| `expiresIn` | integer | Access Token 剩余秒数，固定 `900` |
| `refreshExpiresIn` | integer | 固定 2592000 秒（30 天）；每次成功刷新轮换并重新计时 |
| `user` | `UserSummary` | `id`、`nickname`、`avatarUrl` |
| `certificationStatus` | §4 枚举 | 便于前端首屏直接判断能否发布 |

其余冻结行为：

- Access Token 放在 `Authorization: Bearer <accessToken>`；登录与刷新**不走前端自动刷新逻辑**。
- `USER_DISABLED` 时前端清空本地会话。
- 刷新时轮换令牌并写 `replacedByTokenId`；检测到已撤销令牌被重放时，撤销该用户该设备的全部刷新令牌。
- `POST /auth/logout` 幂等：令牌已撤销仍返回 **204**。

依据：§7.1.1、§8.1。

### 1.7 成功状态码语义

| 场景 | 状态码 | 说明 |
| --- | --- | --- |
| 创建资源 | **201** + 资源详情 | `POST /certifications`、`POST /admin/categories`、`POST /items`、`POST /files`、`POST /orders`、`POST /reviews` |
| 订单幂等重放 | **200** + 原订单 | 同一买家 + 同一 `clientRequestId`，且 `itemId`、`tradeMode` 与原订单一致 |
| 订单幂等键冲突 | **409** `ORDER_DUPLICATE_REQUEST` | 同键但内容不一致 |
| 状态变更 / 更新 | **200** + 最新资源 | 不使用「只返回布尔值」 |
| 删除、下架、收藏、标记已读 | **204** 无响应体 | 删除商品是状态变更 |

依据：§7.1（写接口返回最新资源或明确结果；删除商品返回 204）、§7.1.1（POST 订单 201/200）。

### 1.8 用户资料的两种视图

| 视图 | 出现位置 | 包含 | 绝不包含 |
| --- | --- | --- | --- |
| `UserProfile` | 仅 `GET /users/me`、`PATCH /users/me` | `role`、`status`、`phone`、`campus`、`certificationStatus`、评分、`lastLoginAt`、`version` | — |
| `PublicUser` | `GET /users/{userId}/public` | `nickname`、`avatarUrl`、`campusName`、`certificationStatus`、评分、`accountActive`、`createdAt` | `phone`、`role`、`openid` |
| `UserSummary` | 嵌入商品、订单、评价、消息、审计 | `id`、`nickname`、`avatarUrl` | 其余一切 |

`DISABLED` 用户的历史评价仍可查看，`PublicUser.accountActive` 返回 `false`。
依据：§8.2。

### 1.9 从未申请认证的响应

`GET /certifications/me/latest` 固定返回：

- 从未申请：`{ "status": "NOT_SUBMITTED", "application": null }`
- 已有申请：`{ "status": "<PENDING|APPROVED|REJECTED>", "application": { ...Certification } }`

固定为「状态 + 可空申请对象」两层，避免前端为「无申请」和「有申请」写两套解析路径。
依据：§8.3（响应为「最新申请或 NOT_SUBMITTED」）。

### 1.10 `allowedActions`

`ItemDetail.allowedActions` 与 `OrderDetail.allowedActions` 是**展示提示**，不是权限来源。

- 前端按 `allowedActions` 决定按钮显隐。
- 后端在处理每个写请求时**仍重新校验**状态与身份，返回 `ORDER_ILLEGAL_STATUS_TRANSITION`、`ITEM_NOT_EDITABLE` 等错误码。
- 商品取值：`EDIT`、`PUBLISH`、`OFF_SHELF`、`DELETE`、`FAVORITE`、`BUY`。
- 订单取值：§4 `OrderAction`。

依据：§8.6、§8.8（前端按 `allowedActions` 展示按钮，后端仍重新校验）。

### 1.11 匿名与可选认证

- 每个操作都**显式**声明 `security`，不依赖根级默认值。
- 完全公开：`security: []` —— `POST /auth/wechat-login`、`POST /auth/refresh`、`GET /users/{userId}/public`、`GET /categories/tree`、`GET /items`、`GET /users/{id}/reviews`、`GET /users/{id}/credit`。
- 可选认证：`GET /items/{id}` 使用 `security: [{}, {bearerAuth: []}]`；**带 Token 时**个性化字段 `favorited`、`canBuy`、`isOwner` 必须按当前用户返回，匿名请求时 `favorited` 为 `null`。

依据：§8.6。

### 1.12 `multipart/form-data` 上传

`POST /files?bizType=<FileBizType>`：

| 位置 | 名称 | 说明 |
| --- | --- | --- |
| query | `bizType` | 必填，§4 `FileBizType` |
| body | `file` | 必填，`type: string, format: binary` |

响应只返回 `fileId` 与受控 `url`，**不返回对象存储内部 `objectKey` 或路径**。
依据：§8.5。

### 1.13 `X-Request-Id`

- 只接受 1～64 个 ASCII 字母、数字、下划线或连字符：`^[A-Za-z0-9_-]{1,64}$`。
- 缺失或非法时服务端**重新生成**，不返回错误。
- 响应头与响应体 `requestId` 必须一致；日志 MDC 在 `finally` 清理。

依据：§7.1.1。

### 1.14 绝不外泄的字段

以下内容在任何响应、错误 `message`、日志、审计 `detail` 与示例中都不出现：

`openid`、`unionid`、`session_key`、`token_hash`、Refresh Token 哈希、WeChat AppSecret、认证证据文件的内部路径或 `objectKey`、明文姓名与完整学号、完整手机号（仅本人接口返回 `phone`）。

管理接口即使对 `ADMIN` 也不返回 `openid`。
依据：§8.1、§8.2、§8.11、§5「不向仓库、日志写入密钥和敏感数据」。

---

## 2. 端点与实现任务的归属

| 标签 | 端点 | 归属任务 |
| --- | --- | --- |
| auth | `POST /auth/wechat-login`、`POST /auth/refresh`、`POST /auth/logout` | BE-04 |
| user | `GET /users/me`、`PATCH /users/me`、`GET /users/{userId}/public` | BE-04 |
| certification | `POST /certifications`、`GET /certifications/me/latest`、`GET /admin/certifications`、`POST /admin/certifications/{id}/approve`、`POST /admin/certifications/{id}/reject` | BE-05 |
| category | `GET /categories/tree`、`POST /admin/categories`、`PATCH /admin/categories/{id}`、`POST /admin/categories/{id}/enable`、`POST /admin/categories/{id}/disable` | BE-05 |
| file | `POST /files` | BE-05 |
| item | `POST /items`、`GET /items`、`GET /items/{id}`、`PUT /items/{id}`、`DELETE /items/{id}`、`POST /items/{id}/publish`、`POST /items/{id}/off-shelf`、`GET /users/me/items` | BE-06 |
| item（管理） | `POST /admin/items/{id}/off-shelf` | BE-10 |
| favorite | `PUT /items/{itemId}/favorite`、`DELETE /items/{itemId}/favorite`、`GET /items/{itemId}/favorite-status`、`GET /users/me/favorites` | BE-07 |
| order | `POST /orders`、`GET /orders/{id}`、`POST /orders/{id}/confirm`、`POST /orders/{id}/reject`、`POST /orders/{id}/cancel`、`POST /orders/{id}/deliver`、`POST /orders/{id}/receive`、`GET /users/me/orders` | BE-08 |
| review | `POST /reviews`、`GET /orders/{id}/review-eligibility`、`GET /users/{id}/reviews`、**`GET /users/{id}/credit`** | BE-09 |
| notification | `GET /notifications`、`GET /notifications/unread-count`、`PUT /notifications/{id}/read`、`PUT /notifications/read-all` | BE-09 |
| admin | `GET /admin/users`、`POST /admin/users/{id}/disable`、`POST /admin/users/{id}/enable`、`GET /admin/items`、`GET /admin/orders`、`GET /admin/orders/{id}`、`GET /admin/audit-logs` | BE-10 / QA-01 |

**唯一所有权提示**：`GET /users/{id}/credit` 由 **BE-09（review 模块）**实现并拥有。路径以 `/users/` 开头，容易与 BE-04 的 user 模块冲突，BE-04 不得重复实现；BE-04 只拥有 `/users/me`、`/users/{userId}/public`。

依据：§8.9（`GET /users/{id}/credit` 归评价 API 小节）、`任务包/API-01.md` 实施结果第 5 条。

---

## 3. 已裁定的快速 MVP 产品规则

项目负责人于 2026-09-11 一次性裁定，以下不再作为联调或合并阻塞：

1. **单校区**：系统只使用 `MAIN` 默认校区。API 不提供校区列表，不接收或筛选 `campusId`；用户、认证和商品由后端自动绑定默认校区。
2. **Refresh Token**：30 天；成功刷新时轮换并重新计算 30 天。
3. **认证方式**：当前只开放 `MANUAL`；不接收 `evidenceFileId`。学生证、校园邮箱和证据材料留作后续扩展。
4. **文件访问**：商品图片和头像公开可读；当前 MVP 不开放认证证据上传和访问。
5. **上传限制**：ITEM_IMAGE 最大 5 MB，AVATAR 最大 2 MB；仅 JPEG、PNG、WebP。
6. **POPULAR 排序**：`favorite_count DESC, view_count DESC, published_at DESC, id DESC`。
7. **校区变更**：用户资料不支持修改校区，因此不存在认证跨校区失效问题。

这些规则已经同步到 `openapi.yaml` 和总纲 v2.7。

### 3.1 `PUT /items/{id}` 为全量替换（2026-09-12 裁定）

项目负责人裁定：修改商品是**全量替换**，不采用「字段省略表示保留原值」。

- `version` 与全部业务字段（`title`、`description`、`price`、`originalPrice`、`condition`、`categoryId`、`imageFileIds`）必填；字段省略或显式 `null` 一律 400 `VALIDATION_ERROR`。
- 唯一例外是 `originalPrice`：允许显式 `null`（表示清除原价），但**键必须出现**——「键缺失」与「显式 null」是两件事，前者 400。
- 图片随请求整体替换，不存在「不带 `imageFileIds` 就不动图片」的分支。

背景：契约此前自相矛盾——路径级描述称「本接口为全量替换」，而 `UpdateItemRequest` 的 schema 描述称「字段省略表示保留原值」。前端服务层 `services/item-api.js` 的 `buildWriteBody` 与发布页 `pages/publish/item-form.js` 的 `buildPayload` 只能全量提交，故按全量替换对齐，矛盾消除。

实现后果：Bean Validation 在控制器方法之前触发，因此**所有期望 403/409 的 `PUT` 都必须携带完整合法请求体**，否则会先拿到 400。`ItemApiFlowTests` 已按此调整，并逐个字段断言「省略即 400」。

已同步：总纲 §8.6（修改商品）、`openapi.yaml`（`put:` 描述与 `UpdateItemRequest` 的 `required`/描述）、后端实现与测试。
依据：§8.6；§4 变更流程第 1 条（先改总纲，再改契约）。

### 3.2 个人订单与管理员监管订单分域（2026-09-13 裁定）

- `GET /orders/{id}` 只允许订单买家或卖家；ADMIN 角色不会扩大个人订单权限。
- 管理员查看其他用户订单必须使用 `GET /admin/orders` 与 `GET /admin/orders/{id}`。
- 管理端列表同时返回买家、卖家和商品快照；管理详情复用订单快照/时间线，但 `allowedActions` 固定为空。
- 管理接口只读，不允许管理员借角色执行接单、拒单、取消、交付、收货或评价。
- 管理员自己的买入/卖出订单继续使用 `/users/me/orders` 与 `/orders/{id}`，不会混入全站监管语义。

依据：负责人 2026-09-13 裁定；总纲 §8.8、§8.11。

---

## 4. 变更流程

1. 契约的路径、字段、枚举、状态码或错误码发生变化，先改 `项目全局规划.md`，再改 `openapi.yaml`，然后通知前后端（§「契约变化」）。
2. 任何改动后必须重跑 `scripts/validate-openapi.ps1`，并以非零退出码为失败。
3. 枚举变化顺序：总纲 → 数据库 → Java → OpenAPI → 前端 constants → 测试。
4. 产品决定变化时同步修订总纲、`openapi.yaml` 和前端 constants。
