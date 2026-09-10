-- ============================================================================
-- DB-01 / V4: 评价、站内消息与审计表
-- 目标数据库: MySQL 8 (InnoDB / utf8mb4 / utf8mb4_0900_ai_ci)
-- 建表顺序: reviews -> notifications -> audit_logs
-- 本文件只建结构, 不写入任何业务数据。
-- audit_logs 与 notifications 的 biz/target 标识是跨领域逻辑标识, 不建多态外键。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- reviews: 订单双向评价
-- ----------------------------------------------------------------------------
CREATE TABLE `reviews` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id`    BIGINT UNSIGNED NOT NULL,
    `reviewer_id` BIGINT UNSIGNED NOT NULL,
    `reviewee_id` BIGINT UNSIGNED NOT NULL,
    `rating`      TINYINT UNSIGNED NOT NULL,
    `content`     VARCHAR(500)    NULL,
    `status`      VARCHAR(32)     NOT NULL DEFAULT 'VISIBLE',
    `created_at`  DATETIME(3)     NOT NULL,
    `updated_at`  DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reviews_order_reviewer` (`order_id`, `reviewer_id`),
    KEY `idx_reviews_reviewee_status_created` (`reviewee_id`, `status`, `created_at`),
    KEY `idx_reviews_reviewer` (`reviewer_id`),
    CONSTRAINT `fk_reviews_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_reviews_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_reviews_reviewee` FOREIGN KEY (`reviewee_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_reviews_rating` CHECK (`rating` BETWEEN 1 AND 5),
    CONSTRAINT `chk_reviews_reviewer_not_reviewee` CHECK (`reviewer_id` <> `reviewee_id`),
    CONSTRAINT `chk_reviews_status` CHECK (`status` IN ('VISIBLE', 'HIDDEN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- notifications: 站内业务消息
-- ----------------------------------------------------------------------------
CREATE TABLE `notifications` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `type`       VARCHAR(32)     NOT NULL,
    `title`      VARCHAR(100)    NOT NULL,
    `content`    VARCHAR(500)    NOT NULL,
    `biz_type`   VARCHAR(32)     NOT NULL,
    `biz_id`     BIGINT UNSIGNED NOT NULL,
    `read_at`    DATETIME(3)     NULL,
    `created_at` DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_notifications_user_read_created` (`user_id`, `read_at`, `created_at`),
    CONSTRAINT `fk_notifications_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- audit_logs: 管理操作审计, 只允许新增
-- ----------------------------------------------------------------------------
CREATE TABLE `audit_logs` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `operator_id` BIGINT UNSIGNED NOT NULL,
    `action`      VARCHAR(64)     NOT NULL,
    `target_type` VARCHAR(32)     NOT NULL,
    `target_id`   BIGINT UNSIGNED NOT NULL,
    `request_id`  VARCHAR(64)     NOT NULL,
    `detail_json` JSON            NOT NULL,
    `created_at`  DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_audit_logs_operator_created` (`operator_id`, `created_at`),
    KEY `idx_audit_logs_target` (`target_type`, `target_id`),
    CONSTRAINT `fk_audit_logs_operator` FOREIGN KEY (`operator_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
