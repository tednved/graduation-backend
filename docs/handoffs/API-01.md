# API-01 交接报告

## 1. 基本信息

| 项 | 值 |
| --- | --- |
| 任务 ID | API-01 |
| 任务包版本 | v1.0（2026-09-10） |
| 分支 | `docs/API-01-openapi-contract` |
| 基线 | `origin/main` = `bea0f7b`（DB-02 已 squash 合并，数据库基线 Flyway V6） |
| 状态 | **DONE** —— 产品规则已裁定，OpenAPI 自动校验通过，PR #4 已合并为 `e15baa77` |
| 契约唯一源文件 | `backend/openapi.yaml` |

本任务只交付契约、契约说明与校验脚本，不涉及业务代码、`pom.xml` 或运行时依赖。

---

## 2. 实现结果

对应 `任务包/API-01.md` 实施结果 1～6：

| # | 要求 | 落实 |
| --- | --- | --- |
| 1 | `servers` 给出基础路径，paths 不重复前缀；每操作独立 `operationId`、完整 security/参数/请求体/响应 | `servers: [{ url: /api/v1 }]`；46 条路径、**51 个操作**，`operationId` 全局唯一；每个操作**显式**声明 `security`（不依赖根级默认值）；路径/查询/请求头参数、必填与可空、长度与范围、成功与失败响应逐项声明 |
| 2 | 统一信封、ID 字符串、金额字符串、UTC 毫秒、204 空体、§7.2 错误码映射；分页与排序白名单；`data` 不泛化 | `ApiResponse`/`ApiError`/`PageResponse` 三个信封；`DecimalId`/`MoneyAmount`/`UtcInstant`/`Rating` 四个标量；25 个错误码按 HTTP 状态分组到 7 个 `ErrorNNN` 响应组件并逐组标注 `x-error-codes`；`page>=0`、`size` 1～100 默认 20；排序白名单 `NEWEST/PRICE_ASC/PRICE_DESC/POPULAR`；每个 200/201 响应的 `data` 都指向具体资源结构，无一使用泛化 `object` |
| 3 | 逐项覆盖 11 个模块；订单 BUY/SELL 同一 path 的 `side` 枚举；201 新建与 200 幂等重放分别描述；公开接口可匿名；不泄露 openid/session_key/token_hash/证据内部路径 | 11 个 tag 全覆盖；`GET /users/me/orders` 用单个 path + 必填 `side` 枚举（`OrderSide`）区分买卖；`POST /orders` 同时声明 201（首次创建）与 200（幂等重放）及 409 `ORDER_DUPLICATE_REQUEST`；7 个公开操作写 `security: []`，`GET /items/{id}` 为可选认证 `[{}, {bearerAuth: []}]` 并定义 `favorited` 匿名时为 `null`；`AdminUser`/`PublicUser`/`Certification`/`FileObject` 明确不返回 `openid` 与内部 `objectKey` |
| 4 | 固定 TokenResponse、PATCH、文件访问、上传限制、POPULAR 与单校区规则 | 见 `docs/api/contract-decisions.md` §1 与 §3，全部已裁定 |
| 5 | 前后端字段对照表与安全实例；声明端点归属，防止 user 模块重复实现 `GET /users/{id}/credit` | 见 `docs/api/field-mapping.md`：§1 共同表示规则、§2 错误码→前端动作、§3 逐资源字段对照、§4 安全实例、§5 端点归属（含唯一所有权提示） |
| 6 | `scripts/validate-openapi.*` 提供完整可复现命令；结构与引用校验、operationId 唯一、操作 security/响应完整、枚举/错误码检查；不改 pom、不加运行时依赖 | `scripts/validate-openapi.ps1` 四步校验，固定 `@redocly/cli@2.51.2` 经 `npx --yes` 一次性拉取；任一步失败非零退出 |

---

## 3. 交付物与修改文件

| 文件 | 状态 | 说明 |
| --- | --- | --- |
| `openapi.yaml` | 新增 | OpenAPI 3.1 MVP 契约（3632 行） |
| `docs/api/contract-decisions.md` | 新增 | 表示规则与已裁定快速 MVP 决策 |
| `docs/api/field-mapping.md` | 新增 | 前后端字段对照、错误码动作、安全实例、端点归属 |
| `scripts/validate-openapi.ps1` | 新增 | 校验入口（368 行） |
| `DEVELOPMENT_LOG.md` | 修改 | 只新增/更新 API-01 自身任务行 |

未触碰：Java 源码、`pom.xml`、Maven Wrapper、`db/migration/**`、`common/**`、前端、根目录规划与其他任务包。

---

## 4. 契约规模

| 项 | 数量 |
| --- | --- |
| 路径 / 操作 | 46 / 51 |
| 标签（模块） | 11 |
| `components.schemas` | 68（含 §4 全部 15 个枚举与 `ErrorCode`） |
| `components.parameters` | 13 |
| `components.responses` | 7 |
| §7.2 错误码 | 25 |
| 未决项 | 0；产品规则已全部裁定 |

---

## 5. 验证证据

### 5.1 校验工具与版本

- 工具：`@redocly/cli`，版本 **2.51.2**，经 `npx --yes` 一次性拉取，不写入 `pom.xml`，不新增项目运行时依赖。
- 环境：Windows 11 + Windows PowerShell 5.1；Node v24.20.0 / npm 12.0.2。

### 5.2 验收命令与结果

```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-openapi.ps1
```

退出码 **0**，全部 PASS：

```
PASS  redocly lint 通过：YAML 可解析、OpenAPI 结构合法、全部 $ref 可解析
PASS  bundle 导出成功
PASS  servers 基础路径为 /api/v1
PASS  securityScheme bearerAuth 已定义
PASS  操作完整性检查通过：共 51 个操作，operationId 唯一，security 与响应结构齐备
PASS  §4 全部 15 个枚举取值一致
PASS  ErrorCode 枚举与 §7.2 全集一致（25 个错误码）
PASS  全部错误码的 HTTP 映射一致，且无遗漏、无多余
```

脚本自检项（超出「YAML 可解析」的硬检查）：

1. `operationId` 全局唯一且非空；
2. 每个操作**显式**声明 `security`，且只引用已定义的 `bearerAuth`；
3. 每个操作至少一个 2xx 与一个失败响应；需要 Bearer Token 的操作必须声明 401 与 403；
4. 每个响应有 `description`，非 204 响应有 `content`，失败响应有 `x-error-codes`，204 不带响应体；
5. `servers` 唯一且为 `/api/v1`，任何路径都不得重复该前缀；
6. §4 全部 15 个枚举取值与总纲逐字一致；
7. `ErrorCode` 枚举等于 §7.2 全集，且每个错误码的 HTTP 映射与其所在 `ErrorNNN` 组件一致，无遗漏、无多余。

### 5.3 负例测试（证明失败会真正非零退出）

在契约副本上人为注入缺陷后运行同一脚本：

| 注入缺陷 | 结果 |
| --- | --- |
| 把 `ItemDetail` 的 `$ref` 改成不存在的 `ItemDetailMissing` | lint 阶段失败，退出码 **1** |
| 把 `ItemStatus` 枚举删成 `[DRAFT, ON_SALE, SOLD]` | `FAIL ItemStatus 取值与 §4 不一致…`，退出码 **1** |
| 把 `Error413` 的错误码改成 `FILE_NOT_OWNED` | `FAIL 错误码 FILE_NOT_OWNED 在 §7.2 中映射 HTTP 403，却在 Error413 中被声明为 413` 且 `FAIL §7.2 错误码 FILE_TOO_LARGE 未出现在任何错误响应组件的 x-error-codes 中`，退出码 **1** |

### 5.4 lint 警告说明

`redocly lint --extends=minimal` 报 2 条 `no-unused-components` **警告**（非错误，退出码仍为 0）：

- `CampusStatus`：单校区下仅作为数据库内部枚举保留，不增加校区 API；
- `FileStatus`：`FileObject` 只返回上传后的可用字段，不返回文件生命周期状态。

两者都是 §4 枚举登记表的组成部分，按「§4 全部枚举必须可被校验」保留，不为了消除警告而向资源结构塞入无意义字段。

### 5.5 其他

- `git diff --check origin/main...HEAD`：无输出（见 §7 提交记录）。
- 契约文件 SHA-256：`8994615dd6dee38bb7bb0f45d57f9562af2a06d75e4a920875b488c11bdde62b`（`openapi.yaml`，3632 行）。**该 SHA 在审查期间必须保持稳定**，任何改动都需重新校验并通知两端。
- 业务代码测试本任务不适用；`ApiContractIT` 由后续 BE-03/BE-04 建立并验证实现与契约一致。

---

## 6. 产品决策收口

项目负责人已裁定全部产品项：单校区 `MAIN`、Refresh Token 30 天滚动轮换、仅 MANUAL 认证且不接证据文件、商品图/头像公开、本地图片 5 MB/头像 2 MB、POPULAR 固定排序、用户不可改校区。OpenAPI 与字段映射已同步，未决项为 0。

## 7. 验收记录

项目负责人最新指令取消额外的前后端独立人工逐字段审查门槛。API-01 以自动契约校验、后续实现的 ApiContractIT 和实际联调保证一致性；自动校验通过后已完成验收并合并。

## 8. 变更摘要

相对基线 `bea0f7b`（DB-02）：

- 新增 OpenAPI 3.1 MVP 契约，覆盖总纲 §8 的全部 11 个模块、46 条路径、51 个操作。
- 新增契约决策表并冻结表示规则；单校区和七项快速 MVP 产品规则已全部裁定。
- 新增前后端字段对照表、错误码→前端动作映射、安全请求/响应实例、端点→任务归属表。
- 新增 PowerShell 校验入口，把「YAML 可解析」提升为「OpenAPI 合法 + 操作完整 + 枚举与错误码与总纲逐字一致」，并已用 3 类负例验证非零退出。
- 不改动任何 Java 代码、迁移、依赖或前端文件；`openapi.yaml` 自此成为 API 契约唯一源文件。

---

## 9. 风险与阻塞

- 阻塞：无。
- `CampusStatus`、`FileStatus` 作为数据库枚举保留但暂未被响应引用，Redocly 产生 2 条 unused 警告；不影响契约合法性或实现。
- Docker/CI 不作为当前契约任务门槛；实现阶段使用真实 MySQL 和功能测试。

## 10. 下一任务输入

- API-01 契约冻结（待审查通过）后：**BE-03 公共协议**（统一响应、错误码、鉴权过滤器、`X-Request-Id`、可选认证分支）→ **BE-04 登录与资料** → BE-05…
- 前端 **FE-02 请求库**依赖本契约的 §1 共同表示规则与 §2 错误码动作表。
- 单校区无校区列表接口；BE-04/BE-05/BE-06 从后端绑定 `MAIN`。
