package com.graduation.backend.item.domain;

/**
 * §4 ItemCondition 枚举。
 *
 * <p>库列名是 {@code condition_level}（{@code condition} 是 MySQL 保留字），
 * 契约字段名是 {@code condition}，两者只在 {@link Item} 的列映射处对应。
 */
public enum ItemCondition {
    NEW,
    LIKE_NEW,
    GOOD,
    FAIR
}
