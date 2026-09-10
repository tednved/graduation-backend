# 数据库 ER 文档

本文件是 `backend/src/main/resources/db/migration/` 中 V1～V5 迁移的实体关系说明，属于 DB-01 交付物。
图中的表名、字段名、主外键关系与迁移 SQL 完全一致，不使用计划外或含义模糊的“扩展表”。

- 数据库产品：MySQL 8（InnoDB，`utf8mb4`，`utf8mb4_0900_ai_ci`）
- 业务表数量：15
- 迁移文件：V1～V5，由 Flyway 从空库顺序执行
- 图中 `BIGINT_UNSIGNED` 表示 `BIGINT UNSIGNED`，`INT_UNSIGNED` 表示 `INT UNSIGNED`，`TINYINT_UNSIGNED` 表示 `TINYINT UNSIGNED`

## 1. ER 图

```mermaid
erDiagram
    campuses |o--o{ users : "campus_id"
    campuses ||--o{ certifications : "campus_id"
    campuses ||--o{ items : "campus_id"
    users ||--o{ refresh_tokens : "user_id"
    users ||--o{ file_objects : "owner_id"
    users ||--o{ certifications : "user_id"
    users |o--o{ certifications : "reviewer_id"
    users ||--o{ items : "seller_id"
    users ||--o{ favorites : "user_id"
    users ||--o{ orders : "buyer_id"
    users ||--o{ orders : "seller_id"
    users ||--o{ order_events : "operator_id"
    users ||--o{ reviews : "reviewer_id"
    users ||--o{ reviews : "reviewee_id"
    users ||--o{ notifications : "user_id"
    users ||--o{ audit_logs : "operator_id"
    refresh_tokens |o--o{ refresh_tokens : "replaced_by_token_id"
    file_objects |o--o{ certifications : "evidence_file_id"
    file_objects ||--o{ item_images : "file_object_id"
    categories |o--o{ categories : "parent_id"
    categories ||--o{ items : "category_id"
    items ||--o{ item_images : "item_id"
    items ||--o{ favorites : "item_id"
    items ||--o{ orders : "item_id"
    orders ||--|| order_snapshots : "order_id"
    orders ||--o{ order_events : "order_id"
    orders ||--o{ reviews : "order_id"

    campuses {
        BIGINT_UNSIGNED id PK
        VARCHAR(32) code UK
        VARCHAR(80) name
        VARCHAR(32) status
        DATETIME(3) created_at
        DATETIME(3) updated_at
    }

    users {
        BIGINT_UNSIGNED id PK
        VARCHAR(64) openid UK
        VARCHAR(64) unionid
        VARCHAR(32) role
        VARCHAR(32) status
        VARCHAR(30) nickname
        VARCHAR(500) avatar_url
        VARCHAR(32) phone
        BIGINT_UNSIGNED campus_id FK
        VARCHAR(32) certification_status
        DECIMAL(3,2) average_rating
        INT_UNSIGNED review_count
        BIGINT version
        DATETIME(3) created_at
        DATETIME(3) updated_at
        DATETIME(3) last_login_at
    }

    refresh_tokens {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED user_id FK
        CHAR(64) token_hash UK
        VARCHAR(64) device_id
        DATETIME(3) expires_at
        DATETIME(3) revoked_at
        BIGINT_UNSIGNED replaced_by_token_id FK
        DATETIME(3) created_at
    }

    file_objects {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED owner_id FK
        VARCHAR(32) biz_type
        VARCHAR(255) object_key UK
        VARCHAR(255) original_name
        VARCHAR(100) content_type
        BIGINT_UNSIGNED size_bytes
        CHAR(64) sha256
        VARCHAR(32) status
        DATETIME(3) created_at
        DATETIME(3) bound_at
    }

    certifications {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED user_id FK
        BIGINT_UNSIGNED campus_id FK
        VARCHAR(32) type
        VARCHAR(80) real_name_masked
        VARCHAR(64) student_no_masked
        BIGINT_UNSIGNED evidence_file_id FK
        VARCHAR(32) status
        VARCHAR(200) reject_reason
        BIGINT_UNSIGNED reviewer_id FK
        DATETIME(3) reviewed_at
        DATETIME(3) created_at
        DATETIME(3) updated_at
    }

    categories {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED parent_id FK
        VARCHAR(30) name
        VARCHAR(500) icon_url
        INT_UNSIGNED sort_no
        VARCHAR(32) status
        DATETIME(3) created_at
        DATETIME(3) updated_at
    }

    items {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED seller_id FK
        BIGINT_UNSIGNED category_id FK
        BIGINT_UNSIGNED campus_id FK
        VARCHAR(80) title
        VARCHAR(2000) description
        DECIMAL(10,2) price
        DECIMAL(10,2) original_price
        VARCHAR(32) condition_level
        VARCHAR(32) status
        TINYINT(1) admin_lock
        VARCHAR(200) off_shelf_reason
        INT_UNSIGNED view_count
        INT_UNSIGNED favorite_count
        BIGINT version
        DATETIME(3) published_at
        DATETIME(3) created_at
        DATETIME(3) updated_at
    }

    item_images {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED item_id FK
        BIGINT_UNSIGNED file_object_id FK
        VARCHAR(500) image_url
        TINYINT_UNSIGNED sort_no
        DATETIME(3) created_at
    }

    favorites {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED user_id FK
        BIGINT_UNSIGNED item_id FK
        DATETIME(3) created_at
    }

    orders {
        BIGINT_UNSIGNED id PK
        VARCHAR(64) order_no UK
        BIGINT_UNSIGNED item_id FK
        BIGINT_UNSIGNED buyer_id FK
        BIGINT_UNSIGNED seller_id FK
        VARCHAR(64) client_request_id
        DECIMAL(10,2) amount
        VARCHAR(32) trade_mode
        VARCHAR(32) status
        VARCHAR(200) cancel_reason
        BIGINT version
        DATETIME(3) created_at
        DATETIME(3) updated_at
        DATETIME(3) confirmed_at
        DATETIME(3) delivered_at
        DATETIME(3) completed_at
        DATETIME(3) cancelled_at
    }

    order_snapshots {
        BIGINT_UNSIGNED order_id PK
        VARCHAR(80) item_title
        VARCHAR(500) item_image_url
        DECIMAL(10,2) item_price
        VARCHAR(30) seller_nickname
        VARCHAR(30) buyer_nickname
        DATETIME(3) created_at
    }

    order_events {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED order_id FK
        VARCHAR(32) action
        VARCHAR(32) from_status
        VARCHAR(32) to_status
        BIGINT_UNSIGNED operator_id FK
        VARCHAR(200) remark
        VARCHAR(64) request_id
        DATETIME(3) created_at
    }

    reviews {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED order_id FK
        BIGINT_UNSIGNED reviewer_id FK
        BIGINT_UNSIGNED reviewee_id FK
        TINYINT_UNSIGNED rating
        VARCHAR(500) content
        VARCHAR(32) status
        DATETIME(3) created_at
        DATETIME(3) updated_at
    }

    notifications {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED user_id FK
        VARCHAR(32) type
        VARCHAR(100) title
        VARCHAR(500) content
        VARCHAR(32) biz_type
        BIGINT_UNSIGNED biz_id
        DATETIME(3) read_at
        DATETIME(3) created_at
    }

    audit_logs {
        BIGINT_UNSIGNED id PK
        BIGINT_UNSIGNED operator_id FK
        VARCHAR(64) action
        VARCHAR(32) target_type
        BIGINT_UNSIGNED target_id
        VARCHAR(64) request_id
        JSON detail_json
        DATETIME(3) created_at
    }
```

## 2. 表职责

| 表 | 一句话职责 |
| --- | --- |
| `campuses` | 校区字典，是用户所属校区、认证校区和商品发布校区的取值来源。 |
| `users` | 用户账号、角色、账号状态、认证状态与信用汇总（平均分、评价数）。 |
| `refresh_tokens` | Refresh Token 的 SHA-256 哈希、设备标识、过期时间与轮换/撤销状态，不保存明文。 |
| `file_objects` | 对象存储中文件（头像、商品图片、认证证据）的元数据与绑定状态。 |
| `certifications` | 校园身份认证的提交内容（脱敏）、证据文件与管理员审核结果。 |
| `categories` | 两级商品分类树及其排序、启停状态。 |
| `items` | 二手商品主体，承载价格、成色、上下架状态与浏览/收藏计数。 |
| `item_images` | 商品图片与文件的关联关系及 1～9 的展示顺序。 |
| `favorites` | 用户与商品之间的收藏关系。 |
| `orders` | 订单主表，承载订单号、金额、交易方式与订单状态机。 |
| `order_snapshots` | 下单瞬间冻结的商品标题、图片、价格与双方昵称快照。 |
| `order_events` | 订单状态转换的追加型事件流水，用于时间线与审计。 |
| `reviews` | 订单完成后买卖双方的互评与评分。 |
| `notifications` | 站内业务消息及其已读状态。 |
| `audit_logs` | 管理员操作审计记录，只允许追加。 |

## 3. 只由数据库保证的规则

以下规则由迁移 SQL 的约束直接保证，应用层无需也不得重复实现为“唯一真相”：

1. **主键**：15 张表的主键均为 `BIGINT UNSIGNED NOT NULL AUTO_INCREMENT`；`order_snapshots` 以 `order_id` 作主键，保证一单一快照。
2. **唯一约束**（同名唯一索引）：
   - `uk_campuses_code(code)`
   - `uk_users_openid(openid)`
   - `uk_refresh_tokens_hash(token_hash)`
   - `uk_file_objects_object_key(object_key)`
   - `uk_categories_parent_name(parent_id, name)`
   - `uk_item_images_item_sort(item_id, sort_no)`、`uk_item_images_item_file(item_id, file_object_id)`
   - `uk_favorites_user_item(user_id, item_id)`
   - `uk_orders_order_no(order_no)`、`uk_orders_buyer_request(buyer_id, client_request_id)`
   - `uk_reviews_order_reviewer(order_id, reviewer_id)`
3. **外键存在性与删除策略**：全部 27 个外键均为 `ON UPDATE RESTRICT ON DELETE RESTRICT`，数据库不会级联删除任何历史数据；被引用行仍被引用时删除会失败。
4. **CHECK 约束**：
   - `chk_campuses_status`：`campuses.status IN ('ENABLED','DISABLED')`
   - `chk_users_role`、`chk_users_status`、`chk_users_certification_status`
   - `chk_users_average_rating`：`average_rating` 在 0.00～5.00 之间
   - `chk_categories_sort_no`：`sort_no <= 9999`
   - `chk_items_price`：`price > 0`；`chk_items_original_price`：`original_price IS NULL OR original_price > 0`
   - `chk_item_images_sort_no`：`sort_no BETWEEN 1 AND 9`
   - `chk_orders_buyer_not_seller`：`buyer_id <> seller_id`；`chk_orders_amount`：`amount > 0`
   - `chk_reviews_rating`：`rating BETWEEN 1 AND 5`；`chk_reviews_reviewer_not_reviewee`：`reviewer_id <> reviewee_id`；`chk_reviews_status`：`status IN ('VISIBLE','HIDDEN')`
5. **非空与默认值**：`created_at`/`updated_at` 一律 `NOT NULL` 且无数据库默认值与自动更新；`status`、`version`、计数字段等默认值由 DDL 固定。
6. **类型与精度**：金额为 `DECIMAL(10,2)`，评分汇总为 `DECIMAL(3,2)`，计数为 `INT UNSIGNED`，并发版本为 `BIGINT NOT NULL DEFAULT 0`。
7. **外键列索引**：每个外键列都有可用索引（组合索引的最左前缀或独立索引），保证外键校验和相关查询不使用全表扫描。

## 4. 必须由应用事务保证的规则

MySQL 没有部分唯一索引，因此以下规则无法用普通索引表达，必须由后续任务在应用层事务与并发校验中实现：

1. **单用户一个 PENDING 认证**：`certifications` 无法用普通唯一索引表达“同一 `user_id` 只能有一条 `status='PENDING'`”，由认证模块在事务中先查后写，并以 `CERTIFICATION_PENDING_EXISTS` 返回冲突。
2. **单商品一个活动订单**：`orders` 无法在数据库层限制“同一 `item_id` 只能存在一条 `PENDING_CONFIRMATION`/`CONFIRMED`/`PENDING_RECEIPT` 订单”，由商品乐观锁（`items.version`）作为主控制、订单查询作为二次校验，冲突映射 `ITEM_CONCURRENTLY_RESERVED`。
3. **两级分类**：数据库不阻止三级分类，也不阻止把自己或后代设为父级；由分类模块校验父级必须是一级且启用。
4. **商品图片 1～9 张**：数据库只限制 `sort_no` 落在 1～9，商品至少一张、最多九张的总数由商品模块事务校验。
5. **一级分类同名**：`uk_categories_parent_name(parent_id, name)` 在 `parent_id IS NULL` 时因 MySQL 允许多个 NULL 而不生效，一级分类重名由分类模块校验。
6. **只追加语义**：`order_snapshots`、`order_events`、`audit_logs` 在数据库中只建 INSERT 路径，对应的应用层不得提供 UPDATE/DELETE 用例；历史保留通过业务状态（如 `reviews.status`）而非删除实现。
7. **汇总字段一致性**：`users.average_rating`/`users.review_count`、`items.view_count`/`favorites` 计数由评价、商品与收藏模块在同一事务内维护。
8. **订单与商品状态联动**：下单锁商品、取消释放商品、收货置 `SOLD` 等跨表状态变化必须在同一事务内完成。

## 5. 时间、金额、枚举、版本与删除策略

- **时间**：统一 `DATETIME(3)`，服务端按 UTC 写入；`created_at`/`updated_at` 为 `NOT NULL` 且由应用显式写入，数据库不设置 `DEFAULT CURRENT_TIMESTAMP` 或 `ON UPDATE CURRENT_TIMESTAMP`。业务时间（`published_at`、`confirmed_at`、`delivered_at`、`completed_at`、`cancelled_at`、`reviewed_at`、`revoked_at`、`read_at`、`bound_at`、`last_login_at`）保持可空。
- **金额**：仅 `DECIMAL(10,2)`，对应 Java `BigDecimal`；`items.price`、`items.original_price`、`orders.amount`、`order_snapshots.item_price`。
- **评分**：`reviews.rating` 为 `TINYINT UNSIGNED`（1～5）；汇总 `users.average_rating` 为 `DECIMAL(3,2)`。
- **枚举**：全部使用 `VARCHAR(32)` 保存总纲 §4 的字符串值，不使用 MySQL `ENUM`，不保存 ordinal。涉及表包括 `campuses.status`、`users.role/status/certification_status`、`certifications.type/status`、`categories.status`、`file_objects.biz_type/status`、`items.condition_level/status`、`orders.trade_mode/status`、`order_events.action/from_status/to_status`、`reviews.status`、`notifications.type/biz_type`。
- **版本**：`users.version`、`items.version`、`orders.version` 为 `BIGINT NOT NULL DEFAULT 0`，供 JPA 乐观锁使用；其余表不使用版本列。
- **计数**：`users.review_count`、`items.view_count`、`items.favorite_count` 为 `INT UNSIGNED NOT NULL DEFAULT 0`；`file_objects.size_bytes` 为 `BIGINT UNSIGNED`。
- **布尔**：`items.admin_lock` 为 `TINYINT(1) NOT NULL DEFAULT 0`。
- **删除策略**：不使用通用 `deleted` 列，也不做物理级联删除。商品删除是状态变更（`items.status = 'DELETED'`），文件解绑是 `file_objects.status` 变更，认证与评价通过业务状态管理；所有外键 `ON DELETE RESTRICT` 保护历史数据。
- **不启用的能力**：无触发器、存储过程、事件、视图、分区表、数据库用户；未启用 `FULLTEXT(title, description)`。

## 6. V1～V5 依赖顺序

| 版本 | 文件 | 创建/写入内容 | 依赖 |
| --- | --- | --- | --- |
| V1 | `V1__create_identity_tables.sql` | 依次创建 `campuses` → `users` → `refresh_tokens` | 无（起点） |
| V2 | `V2__create_catalog_tables.sql` | 依次创建 `file_objects` → `certifications` → `categories` → `items` → `item_images` | 依赖 V1 的 `users`、`campuses` |
| V3 | `V3__create_trade_tables.sql` | 依次创建 `favorites` → `orders` → `order_snapshots` → `order_events` | 依赖 V1 的 `users`、V2 的 `items` |
| V4 | `V4__create_review_message_tables.sql` | 依次创建 `reviews` → `notifications` → `audit_logs` | 依赖 V1 的 `users`、V3 的 `orders` |
| V5 | `V5__seed_base_data.sql` | 只写入 1 个校区与 12 条两级分类种子 | 依赖 V1 的 `campuses`、V2 的 `categories` |

V1～V4 只创建结构，V5 只写种子数据；迁移不依赖本机绝对路径、环境变量或外部文件。第二个 `orders` 相关表（`order_snapshots`、`order_events`）以及 `item_images` 依赖前序表先建，因此文件内建表顺序不可调整。

## 7. 数据边界声明

- 迁移中**不创建默认管理员**，不写入任何用户、认证、商品、订单、评价、消息或审计数据。
- V5 只包含固定演示数据：1 个校区（`MAIN` / `默认校区`）与 12 条分类（5 个一级 + 7 个二级），全部为 `ENABLED`，时间为固定 UTC `2026-09-06 00:00:00.000`。
- 不含真实用户、密钥、Token、手机号、学号或其他敏感数据；`refresh_tokens` 只保存哈希，不保存 Refresh Token 明文。
- 管理员账号由后续 `AdminBootstrapRunner`（仅 local/demo Profile）按已存在用户的 openid 提升，不在数据库迁移中产生。
