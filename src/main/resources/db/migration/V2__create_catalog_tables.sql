-- ============================================================================
-- DB-01 / V2: 文件、认证、分类与商品表
-- 目标数据库: MySQL 8 (InnoDB / utf8mb4 / utf8mb4_0900_ai_ci)
-- 建表顺序: file_objects -> certifications -> categories -> items -> item_images
-- 本文件只建结构, 不写入任何业务数据。不启用 FULLTEXT。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- file_objects: 对象存储文件元数据
-- ----------------------------------------------------------------------------
CREATE TABLE `file_objects` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `owner_id`      BIGINT UNSIGNED NOT NULL,
    `biz_type`      VARCHAR(32)     NOT NULL,
    `object_key`    VARCHAR(255)    NOT NULL,
    `original_name` VARCHAR(255)    NOT NULL,
    `content_type`  VARCHAR(100)    NOT NULL,
    `size_bytes`    BIGINT UNSIGNED NOT NULL,
    `sha256`        CHAR(64)        NOT NULL,
    `status`        VARCHAR(32)     NOT NULL DEFAULT 'UPLOADED',
    `created_at`    DATETIME(3)     NOT NULL,
    `bound_at`      DATETIME(3)     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_objects_object_key` (`object_key`),
    KEY `idx_file_objects_owner_biz_status` (`owner_id`, `biz_type`, `status`),
    CONSTRAINT `fk_file_objects_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- certifications: 校园身份认证申请与审核
-- 同一用户只能有一个 PENDING 申请, 由应用事务保证, 数据库不模拟部分唯一索引。
-- ----------------------------------------------------------------------------
CREATE TABLE `certifications` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT UNSIGNED NOT NULL,
    `campus_id`         BIGINT UNSIGNED NOT NULL,
    `type`              VARCHAR(32)     NOT NULL,
    `real_name_masked`  VARCHAR(80)     NOT NULL,
    `student_no_masked` VARCHAR(64)     NULL,
    `evidence_file_id`  BIGINT UNSIGNED NULL,
    `status`            VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    `reject_reason`     VARCHAR(200)    NULL,
    `reviewer_id`       BIGINT UNSIGNED NULL,
    `reviewed_at`       DATETIME(3)     NULL,
    `created_at`        DATETIME(3)     NOT NULL,
    `updated_at`        DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_certifications_user_status` (`user_id`, `status`),
    KEY `idx_certifications_status_created` (`status`, `created_at`),
    KEY `idx_certifications_campus` (`campus_id`),
    KEY `idx_certifications_evidence_file` (`evidence_file_id`),
    KEY `idx_certifications_reviewer` (`reviewer_id`),
    CONSTRAINT `fk_certifications_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_certifications_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_certifications_evidence_file` FOREIGN KEY (`evidence_file_id`) REFERENCES `file_objects` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_certifications_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- categories: 两级商品分类
-- 只支持两级; MySQL 唯一索引允许多个 NULL, 一级分类同名由应用层校验。
-- ----------------------------------------------------------------------------
CREATE TABLE `categories` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `parent_id`  BIGINT UNSIGNED NULL,
    `name`       VARCHAR(30)     NOT NULL,
    `icon_url`   VARCHAR(500)    NULL,
    `sort_no`    INT UNSIGNED    NOT NULL DEFAULT 0,
    `status`     VARCHAR(32)     NOT NULL DEFAULT 'ENABLED',
    `created_at` DATETIME(3)     NOT NULL,
    `updated_at` DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_categories_parent_name` (`parent_id`, `name`),
    KEY `idx_categories_parent_status_sort` (`parent_id`, `status`, `sort_no`),
    CONSTRAINT `fk_categories_parent` FOREIGN KEY (`parent_id`) REFERENCES `categories` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_categories_sort_no` CHECK (`sort_no` <= 9999)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- items: 二手商品
-- 不建 FULLTEXT(title, description); 中文分词验证通过后由新迁移增加。
-- ----------------------------------------------------------------------------
CREATE TABLE `items` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `seller_id`        BIGINT UNSIGNED NOT NULL,
    `category_id`      BIGINT UNSIGNED NOT NULL,
    `campus_id`        BIGINT UNSIGNED NOT NULL,
    `title`            VARCHAR(80)     NOT NULL,
    `description`      VARCHAR(2000)   NOT NULL,
    `price`            DECIMAL(10, 2)  NOT NULL,
    `original_price`   DECIMAL(10, 2)  NULL,
    `condition_level`  VARCHAR(32)     NOT NULL,
    `status`           VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    `admin_lock`       TINYINT(1)      NOT NULL DEFAULT 0,
    `off_shelf_reason` VARCHAR(200)    NULL,
    `view_count`       INT UNSIGNED    NOT NULL DEFAULT 0,
    `favorite_count`   INT UNSIGNED    NOT NULL DEFAULT 0,
    `version`          BIGINT          NOT NULL DEFAULT 0,
    `published_at`     DATETIME(3)     NULL,
    `created_at`       DATETIME(3)     NOT NULL,
    `updated_at`       DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_items_status_published` (`status`, `published_at`),
    KEY `idx_items_category_status_published` (`category_id`, `status`, `published_at`),
    KEY `idx_items_campus_status_published` (`campus_id`, `status`, `published_at`),
    KEY `idx_items_seller_status_created` (`seller_id`, `status`, `created_at`),
    KEY `idx_items_price` (`price`),
    CONSTRAINT `fk_items_seller` FOREIGN KEY (`seller_id`) REFERENCES `users` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_items_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_items_campus` FOREIGN KEY (`campus_id`) REFERENCES `campuses` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_items_price` CHECK (`price` > 0),
    CONSTRAINT `chk_items_original_price` CHECK (`original_price` IS NULL OR `original_price` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- item_images: 商品图片顺序与文件关联
-- 数据库只限制顺序范围, 商品 1~9 张总数由应用事务校验。
-- ----------------------------------------------------------------------------
CREATE TABLE `item_images` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `item_id`        BIGINT UNSIGNED NOT NULL,
    `file_object_id` BIGINT UNSIGNED NOT NULL,
    `image_url`      VARCHAR(500)    NOT NULL,
    `sort_no`        TINYINT UNSIGNED NOT NULL,
    `created_at`     DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_item_images_item_sort` (`item_id`, `sort_no`),
    UNIQUE KEY `uk_item_images_item_file` (`item_id`, `file_object_id`),
    KEY `idx_item_images_file_object` (`file_object_id`),
    CONSTRAINT `fk_item_images_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_item_images_file_object` FOREIGN KEY (`file_object_id`) REFERENCES `file_objects` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_item_images_sort_no` CHECK (`sort_no` BETWEEN 1 AND 9)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
