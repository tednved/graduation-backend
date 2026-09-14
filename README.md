# graduation-backend

校园二手物品交易系统的后端服务。提供微信登录、校园认证、商品发布与搜索、收藏、订单交易、评价、消息通知，以及管理员认证审核、用户管理、商品管理、订单监管和审计日志接口。

上游入口见仓库根目录的 `AGENTS.md` 与 `PROJECT_MEMORY.md`；接口契约以仓库内 `openapi.yaml` 为唯一事实来源。

## 1. 技术基线

| 项 | 版本 / 说明 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.1.1（`spring-boot-starter-webmvc`） |
| 构建 | Maven Wrapper（`./mvnw`，无需本机装 Maven） |
| Maven 坐标 | `com.graduation:backend` |
| 主包 / 启动类 | `com.graduation.backend` / `com.graduation.backend.BackendApplication` |
| 数据库 | MySQL 8.0.17+，`utf8mb4` / `utf8mb4_0900_ai_ci` |
| 迁移 | Flyway，当前最高版本 **V6**，表结构由 Flyway 独占 |
| 持久层 | JPA 负责业务写入，MyBatis-Plus 负责复杂查询，共用同一数据源与事务管理器 |
| 契约 | OpenAPI 3.1（`openapi.yaml`，53 个操作），springdoc 生成文档 UI |
| 安全 | Spring Security + JWT（HS256），无状态，登录接口匿名可访问 |

> MySQL 必须 **8.0.17 及以上**：V6 迁移使用 `utf8mb4_0900_bin` 排序规则，该规则自 8.0.17 起提供。

## 2. 创建数据库

Flyway 只负责在既有 schema 内建表，**不会创建 schema 本身**，首次启动前需要手工建库：

```sql
CREATE DATABASE `graduation-project`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'graduation_dev'@'localhost' IDENTIFIED BY '<你的本地口令>';
GRANT ALL PRIVILEGES ON `graduation-project`.* TO 'graduation_dev'@'localhost';
FLUSH PRIVILEGES;
```

库名中的连字符是既有约定（`graduation-project`），写 SQL 时记得加反引号。

## 3. 本地配置

配置全部在 `src/main/resources/application.properties`，沿用 `.properties` 格式，不引入 profile 文件。关键项：

| 配置 | 作用 |
| --- | --- |
| `spring.datasource.url` | 默认 `jdbc:mysql://127.0.0.1:3306/graduation-project?...`，可用环境变量 `DB_URL` 覆盖 |
| `spring.datasource.username` / `password` | 本机开发库账号 |
| `spring.jpa.hibernate.ddl-auto=validate` | Hibernate 只校验实体映射，不建表改表 |
| `spring.flyway.clean-disabled=true` | 生产语义下禁止 `flyway clean` |
| `app.wechat.mode=mock` | 微信登录走本地 Mock，不请求真实微信服务器 |
| `app.wechat.mock-openid` | 单账号联调时固定登录到同一个用户 |
| `app.file.storage-root` | 本地媒体根目录，默认 `./var/media`，可用 `FILE_STORAGE_ROOT` 覆盖 |
| `app.file.public-base-url=/media` | 媒体对外路径前缀 |

**新环境需要改的一处**：`spring.datasource.username` / `password` 是仓库内固定的本机开发账号，换机器后请改成第 2 步创建的账号。这两项没有做环境变量占位，是刻意的——本地演示要求 IDE 直接可运行。

`application.properties` 与 `src/test/resources/application.properties` 里都**不含**生产凭据、微信 `session_key` 或真实用户数据。

## 4. 启动

```bash
# 在 backend/ 目录下
./mvnw spring-boot:run
```

或在 IDE 中直接运行 `com.graduation.backend.BackendApplication`。默认监听 `8080`，接口基础路径为 `/api/v1`。

首次启动时 Flyway 会依次执行 `db/migration/V1__*.sql` ～ `V6__*.sql`：建表、建立索引与唯一键、写入默认校区 `MAIN` 和两级商品分类种子数据。种子数据**不含**任何用户、认证、商品、订单、评价或管理员账号。

### 健康检查

```bash
curl http://127.0.0.1:8080/actuator/health
```

返回 `{"status":"UP"}` 即可认为服务与数据源就绪。该端点在安全配置中匿名开放，是唯一对外开放的 actuator 端点。

## 5. 测试

集成测试需要**真实 MySQL**（本机无 Docker，因此不走 Testcontainers，也不用 H2 代替），会连接一个一次性 schema 并在每个测试上下文启动时 `clean` + `migrate`，让 V1～V6 从空库真实重放：

```bash
./mvnw test -Ddb.it.username=<账号> -Ddb.it.password=<口令>
```

- 目标 schema 由 `DB_IT_URL` 决定，默认 `jdbc:mysql://127.0.0.1:3306/graduation_mvp_it?createDatabaseIfNotExist=true&...`。
- **schema 名必须以 `_it` 结尾**：测试基类在 `clean()` 之前会校验，防止误清开发库。
- 凭据也可用环境变量 `DB_IT_USERNAME` / `DB_IT_PASSWORD` 提供，`-D` 系统属性优先。
- 未提供凭据时，依赖真实库的测试类会整体跳过而不是让构建失败。

当前用例总数 **63**，全部通过。

## 6. OpenAPI 契约

`openapi.yaml` 是接口契约的唯一事实来源（OpenAPI 3.1，53 个操作），后端 DTO、前端 service 与页面字段都应与之对齐。

查看与校验：

```bash
# 结构与规范校验（redocly 2.51.2 经 npx 一次性拉取，不写入 pom）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-openapi.ps1
```

该脚本分四步：redocly lint、bundle 导出 JSON、契约自检（operationId 唯一、每个操作显式声明 `security`、2xx/4xx 响应完整、204 无响应体、servers 基础路径唯一、枚举取值一致）、错误码全集与 `code → HTTP` 映射一致。

> 注意：springdoc 的 `/v3/api-docs` 与 `/swagger-ui/**` 未在安全配置中匿名开放，浏览器直接打开会返回 401。本地以仓库内 `openapi.yaml` 为准，需要渲染时用 redocly 预览或带 Token 访问。

## 7. 媒体文件

上传的图片写在 `app.file.storage-root`（默认 `./var/media`）下，按业务分组存放（如 `var/media/item_image`）。对外只暴露 `/media/{fileId}` 形式的受控地址，不暴露真实存储路径；媒体路径在安全配置中匿名可读。

清理演示数据时可直接删除该目录下的内容，数据库中的 `file_objects` 记录会随之失去意义，因此**建议连同数据库一起重建**（见第 8 节）。

## 8. 演示环境准备

仓库**不含**种子管理员账号，演示环境按以下方式搭建。

### 8.1 造账号

Mock 登录模式下不请求微信服务器，通过 `POST /api/v1/auth/wechat-login` 传入不同的 `code` 即可造出不同用户。账号之间靠后端签发的 openid 区分，用户 id 在响应中返回。

- 单账号联调：保持 `app.wechat.mock-openid=mock_...` 非空，无论传什么 code 都固定登录到同一个用户，适合在微信开发者工具里反复调同一个账号。
- 多角色演示：把 `app.wechat.mock-openid` 置空（或在启动时用 `APP_WECHAT_MOCK_OPENID=' '` 覆盖），此时按传入的 `code` 区分账号，可造出买家与卖家两个独立用户。

### 8.2 提权与认证态

- **管理员**：项目没有种子管理员。提权走直接改库 `UPDATE users SET role='ADMIN' WHERE id=<用户id>;`，之后重新登录使新 Token 带上管理员角色。
- **已认证状态**：演示认证通过后的效果，直接改库 `UPDATE users SET certification_status='APPROVED' WHERE id=<用户id>;`。
  > 这是既定路径，不是绕过：管理员**不能审核自己的认证申请**（通过与驳回双向封禁，见 `KNOWN_ISSUES.md` KI-03），提权后的账号若去提交认证会一直停在 `PENDING`，因此演示前的认证态只能由直接改库置位。

### 8.3 用 API 准备演示数据

以下命令已在本机实测跑通（登录 → 上传 → 建草稿 → 上架 → 匿名搜索可见）：

```bash
BASE=http://127.0.0.1:8080/api/v1

# 1) 登录拿 Token。多账号模式下不同 code 对应不同用户，返回 data.accessToken 与 data.user.id
curl -s -X POST $BASE/auth/wechat-login \
  -H 'Content-Type: application/json' \
  -d '{"code":"demo-seller","deviceId":"demo-dev"}'

# 2) 按 8.2 节改库：管理员置 role='ADMIN'，卖家/买家置 certification_status='APPROVED'
#    改完必须重新登录，Token 才会带上新角色与认证态

# 3) 上传商品图，响应里的 data.fileId 就是后续要用的文件 id
curl -s -X POST "$BASE/files?bizType=ITEM_IMAGE" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/demo.png"

# 4) 创建商品草稿
cat > item.json <<'JSON'
{"title":"演示商品A","description":"用于答辩演示的示例商品描述文本","price":"88.00","condition":"GOOD","categoryId":"101","imageFileIds":["<fileId>"]}
JSON
curl -s -X POST $BASE/items \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json; charset=utf-8' \
  --data-binary @item.json

# 5) 上架（请求体为空对象）
curl -s -X POST $BASE/items/<itemId>/publish \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{}'
```

`categoryId` 取 `GET /api/v1/categories/tree` 中任一**启用的二级分类**（如 `101` 手机通讯、`201` 教材书籍）。

三个实测踩到的坑：

- **ID 与金额一律是 JSON 字符串，不是数字**。`DecimalId`（`categoryId`、`imageFileIds`）与 `MoneyAmount`（`price`）在契约里都是字符串，分别形如 `"101"` 和 `"88.00"`。传数字会直接返回 `VALIDATION_ERROR`，且响应不会指出是哪个字段。这里的设计动机是避免微信 JavaScript 的 BIGINT 精度丢失。
- **含中文的请求体要按 UTF-8 字节发送**。在 Git Bash 里把中文内联写进 `-d '...'` 可能被终端编码破坏，导致后端解析失败并同样报 `VALIDATION_ERROR`。用 `--data-binary @文件` 并显式声明 `charset=utf-8` 最稳妥。
- **认证与提权必须先做**。认证状态不是 `APPROVED` 时创建商品会返回 `USER_CERTIFICATION_REQUIRED`；管理员能力则要在改库提权并**重新登录**后才生效。

### 8.4 清空并重建演示库

```sql
DROP DATABASE `graduation-project`;
CREATE DATABASE `graduation-project` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

重启后端即可让 Flyway 从空库重新执行 V1～V6，回到只有校区与分类的干净状态。

## 9. 代码结构

按业务域分包，每个域内部再分 `api`（Controller + DTO）、`application`（应用服务、事务边界）、`domain`（实体与领域方法）、`query`（MyBatis-Plus 复杂查询）。

| 包 | 职责 |
| --- | --- |
| `auth` | 微信登录、Token 签发与刷新 |
| `user` | 用户资料、公开主页、信用摘要 |
| `certification` | 校园认证提交与审核 |
| `category` | 分类树与分类管理 |
| `item` | 商品发布、编辑、上下架、搜索、详情 |
| `favorite` | 收藏 |
| `order` | 下单、接单、拒单、交付、收货、取消 |
| `review` | 双方互评与用户评价列表 |
| `notification` | 站内消息与未读数 |
| `file` | 文件上传与 `/media/{fileId}` 读取 |
| `admin` | 管理端认证审核、用户、商品、订单、审计查询 |
| `common` | 安全、错误码与统一异常、审计、通用 Web 与领域基类 |

Controller 不写业务；写操作放在 `application` 层并在此划事务；状态变更一律通过领域方法，不直接 set 字段。

## 10. 订单可见性（重要）

- **个人订单**：`GET /users/me/orders` 与 `GET /orders/{id}` 只认当前账号的买卖身份。管理员若既不是买家也不是卖家，访问 `/orders/{id}` 返回 `403 ORDER_OPERATION_FORBIDDEN`。
- **全站订单监管**：管理端统一走 `GET /admin/orders` 与 `GET /admin/orders/{id}`，只读，不提供买卖双方的操作入口。

不要把管理员旁路写回个人订单接口——该旁路已在 QA-01 中移除。

## 11. 已知问题

非阻断问题见 `KNOWN_ISSUES.md`，当前两条：认证证据不可泄露无法在公开 API 层证伪（KI-01）、管理员不能审核自己的认证（KI-03）；均有绕行方案，不影响演示。
