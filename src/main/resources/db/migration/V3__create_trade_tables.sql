-- ============================================================================
-- DB-01 / V3: 收藏与交易表
-- 目标数据库: MySQL 8 (InnoDB / utf8mb4 / utf8mb4_0900_ai_ci)
-- 建表顺序: favorites -> orders -> order_snapshots -> order_events
-- 本文件只建结构, 不写入任何业务数据。
-- 同一商品的活动订单唯一性由商品乐观锁与应用事务保证, 数据库不模拟部分唯一索引。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- favorites: 用户收藏关系
-- ----------------------------------------------------------------------------
CREATE TABLE `favorites` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `item_id`    BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_favorites_user_item` (`user_id`, `item_id`),
    KEY `idx_favorites_item` (`item_id`),
    CONSTRAINT `fk_favorites_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_favorites_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- orders: 订单主表
-- ----------------------------------------------------------------------------
CREATE TABLE `orders` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_no`          VARCHAR(64)     NOT NULL,
    `item_id`           BIGINT UNSIGNED NOT NULL,
    `buyer_id`          BIGINT UNSIGNED NOT NULL,
    `seller_id`         BIGINT UNSIGNED NOT NULL,
    `client_request_id` VARCHAR(64)     NOT NULL,
    `amount`            DECIMAL(10, 2)  NOT NULL,
    `trade_mode`        VARCHAR(32)     NOT NULL DEFAULT 'OFFLINE',
    `status`            VARCHAR(32)     NOT NULL DEFAULT 'PENDING_CONFIRMATION',
    `cancel_reason`     VARCHAR(200)    NULL,
    `version`           BIGINT          NOT NULL DEFAULT 0,
    `created_at`        DATETIME(3)     NOT NULL,
    `updated_at`        DATETIME(3)     NOT NULL,
    `confirmed_at`      DATETIME(3)     NULL,
    `delivered_at`      DATETIME(3)     NULL,
    `completed_at`      DATETIME(3)     NULL,
    `cancelled_at`      DATETIME(3)     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_orders_order_no` (`order_no`),
    UNIQUE KEY `uk_orders_buyer_request` (`buyer_id`, `client_request_id`),
    KEY `idx_orders_buyer_status_created` (`buyer_id`, `status`, `created_at`),
    KEY `idx_orders_seller_status_created` (`seller_id`, `status`, `created_at`),
    KEY `idx_orders_item_status` (`item_id`, `status`),
    CONSTRAINT `fk_orders_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_orders_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_orders_seller` FOREIGN KEY (`seller_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_orders_buyer_not_seller` CHECK (`buyer_id` <> `seller_id`),
    CONSTRAINT `chk_orders_amount` CHECK (`amount` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- order_snapshots: 下单时冻结的商品与双方昵称快照, 只追加一条, 不提供更新时间列
-- ----------------------------------------------------------------------------
CREATE TABLE `order_snapshots` (
    `order_id`        BIGINT UNSIGNED NOT NULL,
    `item_title`      VARCHAR(80)     NOT NULL,
    `item_image_url`  VARCHAR(500)    NULL,
    `item_price`      DECIMAL(10, 2)  NOT NULL,
    `seller_nickname` VARCHAR(30)     NOT NULL,
    `buyer_nickname`  VARCHAR(30)     NOT NULL,
    `created_at`      DATETIME(3)     NOT NULL,
    PRIMARY KEY (`order_id`),
    CONSTRAINT `fk_order_snapshots_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- order_events: 订单状态事件, 只追加, 不设计 updated_at
-- ----------------------------------------------------------------------------
CREATE TABLE `order_events` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id`    BIGINT UNSIGNED NOT NULL,
    `action`      VARCHAR(32)     NOT NULL,
    `from_status` VARCHAR(32)     NULL,
    `to_status`   VARCHAR(32)     NOT NULL,
    `operator_id` BIGINT UNSIGNED NOT NULL,
    `remark`      VARCHAR(200)    NULL,
    `request_id`  VARCHAR(64)     NOT NULL,
    `created_at`  DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_order_events_order_created` (`order_id`, `created_at`),
    KEY `idx_order_events_operator` (`operator_id`),
    CONSTRAINT `fk_order_events_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_order_events_operator` FOREIGN KEY (`operator_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
