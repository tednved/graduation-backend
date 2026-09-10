-- ============================================================================
-- DB-01 / V1: 身份与访问控制表
-- 目标数据库: MySQL 8 (InnoDB / utf8mb4 / utf8mb4_0900_ai_ci)
-- 建表顺序: campuses -> users -> refresh_tokens
-- 本文件只建结构, 不写入任何业务数据 (种子数据见 V5)。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- campuses: 校区字典
-- ----------------------------------------------------------------------------
CREATE TABLE `campuses` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `code`       VARCHAR(32)     NOT NULL,
    `name`       VARCHAR(80)     NOT NULL,
    `status`     VARCHAR(32)     NOT NULL DEFAULT 'ENABLED',
    `created_at` DATETIME(3)     NOT NULL,
    `updated_at` DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_campuses_code` (`code`),
    CONSTRAINT `chk_campuses_status` CHECK (`status` IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- users: 用户账号与信用汇总
-- ----------------------------------------------------------------------------
CREATE TABLE `users` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `openid`               VARCHAR(64)     NOT NULL,
    `unionid`              VARCHAR(64)     NULL,
    `role`                 VARCHAR(32)     NOT NULL DEFAULT 'USER',
    `status`               VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    `nickname`             VARCHAR(30)     NOT NULL,
    `avatar_url`           VARCHAR(500)    NULL,
    `phone`                VARCHAR(32)     NULL,
    `campus_id`            BIGINT UNSIGNED NULL,
    `certification_status` VARCHAR(32)     NOT NULL DEFAULT 'NOT_SUBMITTED',
    `average_rating`       DECIMAL(3, 2)   NOT NULL DEFAULT 0.00,
    `review_count`         INT UNSIGNED    NOT NULL DEFAULT 0,
    `version`              BIGINT          NOT NULL DEFAULT 0,
    `created_at`           DATETIME(3)     NOT NULL,
    `updated_at`           DATETIME(3)     NOT NULL,
    `last_login_at`        DATETIME(3)     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_openid` (`openid`),
    KEY `idx_users_campus_status` (`campus_id`, `status`),
    CONSTRAINT `fk_users_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_users_role` CHECK (`role` IN ('USER', 'ADMIN')),
    CONSTRAINT `chk_users_status` CHECK (`status` IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT `chk_users_certification_status`
        CHECK (`certification_status` IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT `chk_users_average_rating` CHECK (`average_rating` >= 0.00 AND `average_rating` <= 5.00)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- refresh_tokens: Refresh Token 元数据, 只保存 SHA-256 哈希, 不保存明文
-- ----------------------------------------------------------------------------
CREATE TABLE `refresh_tokens` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`               BIGINT UNSIGNED NOT NULL,
    `token_hash`            CHAR(64)        NOT NULL,
    `device_id`             VARCHAR(64)     NOT NULL,
    `expires_at`            DATETIME(3)     NOT NULL,
    `revoked_at`            DATETIME(3)     NULL,
    `replaced_by_token_id`  BIGINT UNSIGNED NULL,
    `created_at`            DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refresh_tokens_hash` (`token_hash`),
    KEY `idx_refresh_tokens_user_revoked` (`user_id`, `revoked_at`),
    KEY `idx_refresh_tokens_expires` (`expires_at`),
    KEY `idx_refresh_tokens_replacement` (`replaced_by_token_id`),
    CONSTRAINT `fk_refresh_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_refresh_tokens_replacement` FOREIGN KEY (`replaced_by_token_id`) REFERENCES `refresh_tokens` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
