# API 契约决策表（API-01）

本文件是 `backend/openapi.yaml` 的配套说明，用于固定「表示规则」并集中登记**尚未由总纲决定**的选项。

- 契约唯一源文件：`backend/openapi.yaml`（OpenAPI 3.1）。
- 校验入口：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-openapi.ps1`。
- 本文件中的「已冻结」表示 API-01 依 `项目全局规划.md` §7.1.1 与 §8 直接确定，前后端实现必须一致。
- 「未决」表示总纲未决定、且一旦决定会改变 API 或产品行为；API-01 不自行冻结，只给出可执行建议，等待项目负责人裁定。

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
| `campusId` | 保留原值 | 清除校区 |
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
| `refreshExpiresIn` | integer | Refresh Token 剩余秒数，具体 TTL 见 §3 未决项 |
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
| admin | `GET /admin/users`、`POST /admin/users/{id}/disable`、`POST /admin/users/{id}/enable`、`GET /admin/items`、`GET /admin/audit-logs` | BE-10 |

**唯一所有权提示**：`GET /users/{id}/credit` 由 **BE-09（review 模块）**实现并拥有。路径以 `/users/` 开头，容易与 BE-04 的 user 模块冲突，BE-04 不得重复实现；BE-04 只拥有 `/users/me`、`/users/{userId}/public`。

依据：§8.9（`GET /users/{id}/credit` 归评价 API 小节）、`任务包/API-01.md` 实施结果第 5 条。

---

## 3. 未决项（上交项目负责人裁定）

以下选项总纲未决定，且一旦确定会改变 API 或产品行为。API-01 已保证：**表示规则类未决项为 0**（§1 全部冻结）；下列为需要裁定的产品级选项，契约中已预留字段但未擅自锁定语义。

### 未决 1：缺少校区列表接口（影响最大）

- **现状**：§8 没有校区查询接口，但 `PATCH /users/me` 需要 `campusId`、`POST /items` 需要 `campusId`、`GET /items` 支持 `campusId` 筛选，`GET /admin/certifications` 支持 `campusId` 筛选。前端目前**无法获取可选校区列表**，校区选择器无法渲染。
- **建议**：新增公开接口 `GET /api/v1/campuses`，`security: []`，只返回 `ENABLED` 校区，响应 `data: { campuses: [{ id, name }] }`；按 `sort_no`/`id` 稳定排序。该接口会用到已定义的 `CampusStatus` 枚举。
- **代价**：需要修订总纲 §8 并补充一个任务归属（建议 BE-05，与分类/认证同批，因为它同属基础数据）。

### 未决 2：Refresh Token 有效期

- **现状**：总纲只固定 Access Token 为 15 分钟，未给 Refresh TTL；契约中 `refreshExpiresIn` 已预留但值未定。
- **建议**：30 天绝对有效期，刷新时轮换新令牌并从刷新时刻重新计时（滑动续期）；到期或重放即要求重新登录。

### 未决 3：认证证据文件是否强制

- **现状**：§8.3 只说「按类型检查 `evidenceFileId`」，未给各类型的强制规则。
- **建议**：`STUDENT_CARD`、`CAMPUS_EMAIL` **必填**；`MANUAL` 可选。缺省要求缺失时返回 `VALIDATION_ERROR`。

### 未决 4：私有认证证据文件的访问策略

- **现状**：`CERTIFICATION_EVIDENCE` 含身份证件类隐私材料。契约只返回 `evidenceFileId`，不返回内部路径；但「管理员审核时如何查看证据、本人能否回看」未定义。
- **建议**：证据文件**不签发公开直链**；仅本人与管理员可读，且由后端鉴权后代理读取或签发短时（≤5 分钟）一次性地址；`FileObject.url` 对该 `bizType` 返回该短时地址而非永久地址。

### 未决 5：商品图片与头像的访问策略

- **现状**：总纲只说明本地图片存储默认 `LOCAL`，未定义可读性。
- **建议**：`ITEM_IMAGE`、`AVATAR` 为公开可读（商品与公开资料本身公开），返回稳定 URL；与未决 4 的私密策略分开实现。

### 未决 6：上传大小与 MIME 上限

- **现状**：§8.5 说明由 `FilePolicy` 校验 MIME、扩展名、文件头与大小，但未给具体数值；契约只固定了 `FILE_TOO_LARGE`（413）与 `FILE_INVALID_TYPE`（400）。
- **建议**：商品图片与头像 ≤ 5 MB（`image/jpeg`、`image/png`、`image/webp`）；认证证据 ≤ 10 MB（另允许 `application/pdf`）。数值应写入 `FilePolicy` 并由 BE-05 的测试固定。

### 未决 7：`POPULAR` 排序的排序键

- **现状**：§8.6 只给出排序白名单，未定义 `POPULAR` 的具体排序规则。
- **建议**：`favorite_count DESC, view_count DESC, published_at DESC, id DESC`（末位加 `id` 保证稳定分页，避免翻页抖动）。

### 未决 8：资料校区变更与已通过认证的关系

- **现状**：§8.2 要求 `PATCH /users/me` 校验校区存在且启用，但未说明已认证用户改校区是否影响认证状态。
- **建议**：允许自由变更资料校区，**不影响**已通过的认证状态；认证记录保留提交时的校区快照。

---

## 4. 变更流程

1. 契约的路径、字段、枚举、状态码或错误码发生变化，先改 `项目全局规划.md`，再改 `openapi.yaml`，然后通知前后端（§「契约变化」）。
2. 任何改动后必须重跑 `scripts/validate-openapi.ps1`，并以非零退出码为失败。
3. 枚举变化顺序：总纲 → 数据库 → Java → OpenAPI → 前端 constants → 测试。
4. 未决项被裁定后，从 §3 移入 §1 并同步修订 `openapi.yaml`、总纲与前端 constants。
