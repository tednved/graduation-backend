-- ----------------------------------------------------------------------------
-- DB-02: 不透明标识符改为逐字符、区分大小写的比较
-- ----------------------------------------------------------------------------
-- 缺陷来源：V1～V4 建表时未对这 6 个标识符列做列级 COLLATE，它们继承表默认排序规则
-- utf8mb4_0900_ai_ci，使 uk_users_openid 等唯一键与等值比较大小写不敏感：
-- 同一用户的 OpenID 换一种大小写会被判为重复，object_key / device_id / order_no /
-- client_request_id 的查询与幂等校验同样被折叠。
--
-- V1～V5 已执行且永久只读，因此按总纲 v2.6 §5.4 用新增迁移修正，不回写旧迁移。
--
-- 影响范围仅限下列 6 个不透明标识符列，不做整表转换：
--   users.openid, users.unionid
--   refresh_tokens.device_id
--   file_objects.object_key
--   orders.order_no, orders.client_request_id
-- 展示文本（nickname、title、description、original_name 等）与搜索文本继续使用表默认
-- utf8mb4_0900_ai_ci，不受本迁移影响。
--
-- 每个 MODIFY COLUMN 都原样保留字符集、长度与 NOT NULL/NULL，未改默认值；列的位置不变，
-- 其上及包含它的既有索引（uk_users_openid、uk_file_objects_object_key、uk_orders_order_no、
-- uk_orders_buyer_request）随列排序规则一并生效，不会被删除或改名。
--
-- 要求 MySQL 8.0.17+：utf8mb4_0900_bin 自该版本起提供。
-- ----------------------------------------------------------------------------

ALTER TABLE `users`
    MODIFY COLUMN `openid`
        VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    MODIFY COLUMN `unionid`
        VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL;

ALTER TABLE `refresh_tokens`
    MODIFY COLUMN `device_id`
        VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL;

ALTER TABLE `file_objects`
    MODIFY COLUMN `object_key`
        VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL;

ALTER TABLE `orders`
    MODIFY COLUMN `order_no`
        VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    MODIFY COLUMN `client_request_id`
        VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL;
