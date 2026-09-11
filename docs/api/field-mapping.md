# 前后端字段对照表（API-01）

- 契约唯一源文件：`backend/openapi.yaml`；表示规则与已裁定产品规则见 `docs/api/contract-decisions.md`。
- 「前端建议用法」是给 FE-02/FE-03 起的参考，不构成新的契约；以后端契约为准。
- 本文件不含任何真实令牌、`openid`、`session_key`、手机号或学号；示例一律为占位值。

---

## 1. 两端共同遵守的表示规则

| 规则 | 后端产出 | 前端处理 |
| --- | --- | --- |
| ID | 十进制**字符串**（`"1024"`） | 原样透传，**禁止** `parseInt` 后再拼接或比较；仅用于展示或作为参数回传 |
| 金额 | 两位小数**字符串**（`"10.00"`） | 直接展示；需要计算时用整数分或字符串运算，禁止浮点累加 |
| 时间 | UTC ISO 8601 毫秒字符串（`"2026-09-10T04:50:00.000Z"`） | `new Date(iso)` 后按时区格式化；不要自己解析字符串 |
| 枚举 | §4 字符串值（`"ON_SALE"`） | constants 用字符串常量做 key，用 `.value` 存原值、`.label` 存中文；不要用数字下标 |
| 分页 | `data.items` + `page/size/totalElements/totalPages/hasNext` | 用 `hasNext` 判断是否还有下一页，不要用 `page*size < totalElements` 自己推 |
| 空值 | 可空字段显式为 `null` | 用 `!= null` 判断，不要用真值判断（`0`、`""` 是合法值） |
| 204 | **无响应体** | 请求库对 204 不要尝试 `JSON.parse` |
| 错误 | `data` 恒为 `null`，`code` 是 §7.2 错误码 | 按 `code` 分支，不要按 `message` 文本匹配 |

依据：`项目全局规划.md` §7.1.1。

---

## 2. 错误码 → 前端处理动作

| `code` | HTTP | 前端动作 |
| --- | --- | --- |
| `AUTH_UNAUTHORIZED` | 401 | 触发一次刷新；刷新失败则清空会话并跳登录 |
| `AUTH_REFRESH_INVALID` | 401 | 直接清空会话并跳登录（不重试） |
| `AUTH_INVALID_CODE` | 401 | 登录页提示重新授权 |
| `USER_DISABLED` | 403 | 清空会话，提示账号已禁用 |
| `AUTH_FORBIDDEN` | 403 | 提示无权限，返回上一页 |
| `USER_CERTIFICATION_REQUIRED` | 403 | 引导跳转校园认证页 |
| `VALIDATION_ERROR` | 400 | 按字段级提示；`message` 可展示 |
| `FILE_INVALID_TYPE` / `FILE_TOO_LARGE` | 400 / 413 | 上传前本地预校验，命中后提示可接受的类型与大小 |
| `RESOURCE_NOT_FOUND` | 404 | 展示空态或「已失效」占位，不弹全局错误 |
| `ITEM_*` / `ORDER_*` / `REVIEW_*` / `CERTIFICATION_*` / `CATEGORY_IN_USE` | 409 / 403 | 用 `message` 做 toast，并刷新当前资源的最新状态 |
| `INTERNAL_ERROR` | 500 | 通用错误提示，附 `requestId` 便于排查 |

登录与刷新接口本身**不参与**自动刷新重试（避免死循环）。依据：§7.1.1、§7.2。

---

## 3. 逐资源字段对照

### 3.1 认证 `TokenResponse`（`POST /auth/wechat-login`、`POST /auth/refresh`）

| 接口字段 | 类型 | 前端建议用法 | 备注 |
| --- | --- | --- | --- |
| `accessToken` | string | 存内存 + 本地缓存，注入 `Authorization: Bearer` | 15 分钟有效 |
| `refreshToken` | string | 持久化，仅用于刷新与登出 | 每次刷新都会换新，必须覆盖旧值 |
| `tokenType` | string | 固定 `Bearer` | 拼请求头用 |
| `expiresIn` | integer | 固定 `900`；用于提前刷新计时 | 建议剩余 < 60s 时预刷新 |
| `refreshExpiresIn` | integer | 用于判断是否需要重新登录 | 固定 2592000 秒 |
| `user.id` | ID 字符串 | 全局当前用户标识 | — |
| `user.nickname` / `user.avatarUrl` | string / string? | 首屏昵称与头像 | `avatarUrl` 可能为 `null` |
| `certificationStatus` | §4 枚举 | 决定「发布」按钮是否可点 | 无需额外请求 |

### 3.2 用户

| 接口 | 字段 | 类型 | 前端建议用法 |
| --- | --- | --- | --- |
| 请求 `wechat-login` | `code` | string | `wx.login()` 返回值，一次性 |
| 请求 `wechat-login` / `refresh` | `deviceId` | string ≤64 | 本地生成并持久化 |
| 请求 `logout` | `refreshToken` | string | 清会话前提交 |
| 请求 `PATCH /users/me` | `nickname` | string 1–20 | 省略=不变；**不得传 null** |
| | `phone` | string? | 传 `null` 表示清除；格式 `1[3-9]xxxxxxxxx` |
| | `avatarFileId` | ID? | 必须来自 `POST /files?bizType=AVATAR`；传 `null` 表示清除 |
| 响应 `UserProfile` | `id`,`role`,`status` | ID / 枚举 | `role=ADMIN` 才显示管理入口 |
| | `phone` | string? | 仅本人接口返回 |
| | `campus.{id,name}` | 对象? | 可能为 `null` |
| | `averageRating` | 金额字符串 | 直接展示，如 `4.67` |
| | `reviewCount` | integer | — |
| | `version` | integer | 并发保护，不要展示 |
| 响应 `PublicUser` | `accountActive` | boolean | `false` 时隐藏「下单」入口 |

### 3.3 校园认证（BE-05）

| 接口 | 字段 | 类型 | 前端建议用法 |
| --- | --- | --- | --- |
| | `type` | §4 枚举 | `STUDENT_CARD`/`CAMPUS_EMAIL`/`MANUAL` |
| | `realName` | string 2–30 | 明文提交；响应只回 `realNameMasked` |
| | `studentNo` | string 4–32 | 明文提交；响应只回 `studentNoMasked` |

| 响应 `CertificationLatest` | `status` | §4 枚举 | `NOT_SUBMITTED` 时 `application` 为 `null` |
| | `application.rejectReason` | string? | `REJECTED` 时展示 |
| 管理 `POST .../{id}/reject` | `reason` | string 2–200 | 必填 |

### 3.4 分类与文件（BE-05）

| 接口 | 字段 | 类型 | 前端建议用法 |
| --- | --- | --- | --- |
| 响应 `CategoryTree` | `categories[].id/name/iconUrl` | — | 一级分类 |
| | `categories[].children[]` | 数组 | 二级分类；仅一级节点有 |
| 请求 `POST /admin/categories` | `name`,`parentId`(null=一级),`iconUrl`,`sortNo` | — | 同级名称唯一 |
| 请求 `POST /files` | query `bizType` | §4 枚举 | `ITEM_IMAGE`/`AVATAR`/`CERTIFICATION_EVIDENCE` |
| | body `file` | binary | 字段名固定为 `file` |
| 响应 `FileObject` | `fileId` | ID | **只把它提交给业务接口**，不要提交 `url` |
| | `url` | string | 仅用于预览 |
| | `sha256`,`sizeBytes` | — | 一般不需要前端使用 |

### 3.5 商品（BE-06 / 管理下架 BE-10）

| 接口 | 字段 | 类型 | 前端建议用法 |
| --- | --- | --- | --- |
| 请求 `POST /items` | `title` | string 2–80 | — |
| | `description` | string 10–2000 | — |
| | `price` | 金额字符串 | 输入转字符串，保留两位 |
| | `originalPrice` | 金额字符串? | ≥ `price` |
| | `condition` | §4 枚举 | `NEW`/`LIKE_NEW`/`GOOD`/`FAIR` |
| | `categoryId` | ID | 必须是**二级**分类 |
| | `imageFileIds` | ID[] 1–9 | 有序，首图即封面；全部来自 `POST /files` |
| 请求 `PUT /items/{id}` | 同上 + `version` | — | `version` 必填，来自上次读取的 `version` |
| 响应 `ItemCard` | `id`,`title`,`price`,`originalPrice`,`condition`,`status`,`coverImageUrl`,`campus`,`category`,`favoriteCount`,`viewCount`,`publishedAt` | — | 列表卡片只用这些字段 |
| 响应 `ItemDetail` | `images[]` | `{fileId,url,sortNo}[]` | 轮播；按 `sortNo` 排 |
| | `seller` | `UserSummary` | 卖家摘要 |
| | `favorited` | boolean? | 匿名请求为 `null`，用 `=== true` 判断 |
| | `isOwner` | boolean | 本人则显示编辑/下架/删除 |
| | `canBuy` | boolean | 决定「立即购买」按钮 |
| | `allowedActions` | 字符串数组 | 仅控制显隐；后端仍会重新校验 |
| | `adminLock`,`offShelfReason` | — | 被管理员锁定时提示卖家 |
| 响应 `MyItemSummary` | 同卡片 + `adminLock`,`offShelfReason`,`version` | — | 我的发布列表 |
| 请求 `POST /admin/items/{id}/off-shelf` | `reason` | string 2–200 | 强制下架，必填 |

### 3.6 收藏（BE-07）

| 接口 | 字段 | 类型 | 前端建议用法 |
| --- | --- | --- | --- |
| `PUT`/`DELETE /items/{itemId}/favorite` | — | 204 | 两者都幂等；成功后本地翻转状态即可 |
| `GET /items/{itemId}/favorite-status` | `favorited` | boolean | 详情页进入时查询 |
| `GET /users/me/favorites` | `data.items[]` | `ItemCard` | 收藏列表 |

### 3.7 订单（BE-08）

| 接口 | 字段 | 类型 | 前端建议用法 |
| --- | --- | --- | --- |
| 请求 `POST /orders` | `itemId` | ID | — |
| | `tradeMode` | §4 枚举 | MVP 只用 `OFFLINE` |
| | `clientRequestId` | string ≤64 | 每次下单页面进入时生成一次并复用；重试不要换新值 |
| 响应 | 首次 201 / 重放 200 | — | 两者结构相同，前端不必区分，都按成功处理 |
| 查询 `GET /users/me/orders` | `side` | `BUY`/`SELL` | **必填**，同一个接口切换买/卖视角 |
| | `status` | §4 枚举? | 省略=全部 |
| 响应 `OrderSummary` | `counterpart` | `UserSummary` | `BUY` 时是对端卖家，`SELL` 时是对端买家 |
| 响应 `OrderDetail` | `item` | 快照 `{itemId,title,imageUrl,price}` | 用快照展示，不要回查商品（商品可能已改） |
| | `allowedActions` | §4 `OrderAction[]` | 按此渲染接单/拒单/取消/交付/收货按钮 |
| | `events[]` | `{action,fromStatus,toStatus,operator,remark,createdAt}[]` | 订单时间线 |
| 请求 `reject` / `cancel` | `reason` | string 2–200 | 必填 |

### 3.8 评价与信用（BE-09）

| 接口 | 字段 | 类型 | 前端建议用法 |
| --- | --- | --- | --- |
| 请求 `POST /reviews` | `orderId` | ID | — |
| | `rating` | integer 1–5 | 星级 |
| | `content` | string? ≤500 | 可选 |
| 响应 `ReviewEligibility` | `canReview` | boolean | 决定是否显示「评价」入口 |
| | `myReviewId` | ID? | 非空表示已评价 |
| | `counterpartReviewed` | boolean | 用于提示「对方已评价」 |
| 响应 `Review` | `reviewer`,`reviewee` | `UserSummary` | 双方摘要 |
| 响应 `UserCredit` | `averageRating`,`reviewCount` | — | 信用摘要 |
| | `distribution` | `{"1".."5": integer}` | 评分分布条；**键是字符串** |

`GET /users/{id}/credit` 由 **BE-09** 实现，BE-04 不得重复实现。依据：契约决策表 §2。

### 3.9 消息（BE-09）

| 接口 | 字段 | 类型 | 前端建议用法 |
| --- | --- | --- | --- |
| 查询 `GET /notifications` | `type` | §4 枚举? | 省略=全部 |
| | `read` | boolean? | 省略=全部 |
| 响应 `Notification` | `type` | §4 枚举 | 决定图标与跳转目标 |
| | `bizType`,`bizId` | string? / ID? | 无关联时省略；据此跳订单或商品 |
| | `read`,`readAt` | boolean / time? | 未读样式 |
| `GET /notifications/unread-count` | `count` | integer | 红点数字 |
| `PUT /notifications/{id}/read` | — | 204 | 幂等 |
| `PUT /notifications/read-all` | `updatedCount` | integer | 本次置为已读的条数 |

### 3.10 管理（BE-10）

| 接口 | 字段 | 类型 | 前端建议用法 |
| --- | --- | --- | --- |
| `GET /admin/users` | `keyword`,`status`,`certificationStatus`,`page`,`size` | 查询 | 用户筛选 |
| 响应 `AdminUser` | `phone` | string? | 管理员可见；**永不返回 `openid`** |
| `POST /admin/users/{id}/disable` | — | `AdminUser` | 不能禁用自己（`AUTH_FORBIDDEN`） |
| `POST /admin/users/{id}/enable` | — | `AdminUser` | 不恢复认证状态 |
| `GET /admin/items` | `status[]`,`sellerId`,`keyword` | 查询 | `status` 可重复传多个 |
| `GET /admin/audit-logs` | `operatorId`,`action`,`targetType`,`targetId`,`from`,`to` | 查询 | 只读，无写入接口 |
| 响应 `AuditLog` | `detail` | object? | 已脱敏；缺失即 `null` |

---

## 4. 安全的请求 / 响应实例

以下实例只使用占位值，不含任何真实令牌或个人信息。

### 4.1 创建商品（请求）

```http
POST /api/v1/items
Authorization: Bearer <accessToken>
X-Request-Id: 01JXXXXXXXXXXXX
Content-Type: application/json

{
  "title": "九成新自行车",
  "description": "骑了半年，车况良好，校内自提。",
  "price": "320.00",
  "originalPrice": "699.00",
  "condition": "LIKE_NEW",
  "categoryId": "12",
  "imageFileIds": ["501", "502"]
}
```

### 4.2 创建商品（成功响应，201）

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": "9021",
    "title": "九成新自行车",
    "description": "骑了半年，车况良好，校内自提。",
    "price": "320.00",
    "originalPrice": "699.00",
    "condition": "LIKE_NEW",
    "status": "DRAFT",
    "images": [
      { "fileId": "501", "url": "https://example.invalid/f/501", "sortNo": 0 },
      { "fileId": "502", "url": "https://example.invalid/f/502", "sortNo": 1 }
    ],
    "seller": { "id": "1024", "nickname": "同学A", "avatarUrl": null },
    "category": { "id": "12", "name": "交通工具" },
    "campus": { "id": "1", "name": "主校区" },
    "favorited": null,
    "isOwner": true,
    "canBuy": false,
    "allowedActions": ["EDIT", "PUBLISH", "DELETE"],
    "adminLock": false,
    "offShelfReason": null,
    "favoriteCount": 0,
    "viewCount": 0,
    "publishedAt": null,
    "createdAt": "2026-09-10T04:50:00.000Z",
    "updatedAt": "2026-09-10T04:50:00.000Z",
    "version": 0
  },
  "requestId": "01JXXXXXXXXXXXX",
  "timestamp": "2026-09-10T04:50:00.000Z"
}
```

### 4.3 下单幂等重放（成功响应，200）

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": "7701",
    "orderNo": "O20260910000001",
    "status": "PENDING_CONFIRMATION",
    "tradeMode": "OFFLINE",
    "amount": "320.00",
    "cancelReason": null,
    "item": { "itemId": "9021", "title": "九成新自行车", "imageUrl": "https://example.invalid/f/501", "price": "320.00" },
    "buyer": { "id": "1024", "nickname": "同学B", "avatarUrl": null },
    "seller": { "id": "1025", "nickname": "同学A", "avatarUrl": null },
    "allowedActions": ["CANCEL"],
    "events": [
      { "id": "1", "action": "CREATE", "fromStatus": null, "toStatus": "PENDING_CONFIRMATION", "operator": { "id": "1024", "nickname": "同学B", "avatarUrl": null }, "remark": null, "createdAt": "2026-09-10T04:50:00.000Z" }
    ],
    "createdAt": "2026-09-10T04:50:00.000Z",
    "confirmedAt": null,
    "deliveredAt": null,
    "completedAt": null,
    "cancelledAt": null,
    "updatedAt": "2026-09-10T04:50:00.000Z"
  },
  "requestId": "01JXXXXXXXXXXXX",
  "timestamp": "2026-09-10T04:50:01.000Z"
}
```

### 4.4 失败响应（409）

```json
{
  "code": "ITEM_CONCURRENTLY_RESERVED",
  "message": "该商品已被其他买家锁定，请稍后重试",
  "data": null,
  "requestId": "01JXXXXXXXXXXXX",
  "timestamp": "2026-09-10T04:50:01.000Z"
}
```

示例中刻意不出现：`openid`、`session_key`、`token_hash`、证据文件内部路径、明文姓名或完整学号。

---

## 5. 验收记录

- 项目负责人已裁定单校区与其余快速 MVP 规则。
- 取消前后端独立人工逐字段审查门槛；以 OpenAPI 自动校验、实现阶段契约测试和实际联调为准。
- API-01 契约校验通过后可直接转 REVIEW 并合并。
