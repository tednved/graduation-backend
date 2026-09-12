package com.graduation.backend.favorite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 收藏关系。
 *
 * <p>只表达「谁收藏了什么」，没有状态字段：取消收藏即删除行。
 * 并发下由 {@code uk_favorites_user_item} 兜底保证不会出现重复行。
 *
 * <p>{@code items.favorite_count} 是冗余计数，只由 {@code ItemRepository} 的原子 SQL 维护，
 * 本聚合不持有也不修改该计数，避免两处写同一行。
 */
@Entity
@Table(name = "favorites")
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Favorite() {
    }

    private Favorite(Long userId, Long itemId, Instant createdAt) {
        this.userId = userId;
        this.itemId = itemId;
        this.createdAt = createdAt;
    }

    public static Favorite of(Long userId, Long itemId, Instant now) {
        return new Favorite(userId, itemId, now);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getItemId() {
        return itemId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
