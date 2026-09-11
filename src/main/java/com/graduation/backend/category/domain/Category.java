package com.graduation.backend.category.domain;

import com.graduation.backend.common.domain.CategoryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 两级商品分类。
 *
 * <p>{@code parentId} 为 {@code null} 表示一级分类。父子关系一旦建立不允许通过管理接口改变，
 * 因此这里只暴露重命名、图标与排序的修改方法。
 */
@Entity
@Table(name = "categories")
public class Category {

    public static final int MAX_SORT_NO = 9999;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CategoryStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {
    }

    private Category(Long parentId, String name, String iconUrl, Integer sortNo, Instant now) {
        this.parentId = parentId;
        this.name = name;
        this.iconUrl = iconUrl;
        this.sortNo = sortNo;
        this.status = CategoryStatus.ENABLED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Category create(Long parentId, String name, String iconUrl, Integer sortNo, Instant now) {
        return new Category(parentId, name, iconUrl, sortNo, now);
    }

    public void rename(String name, Instant now) {
        this.name = name;
        this.updatedAt = now;
    }

    public void changeIcon(String iconUrl, Instant now) {
        this.iconUrl = iconUrl;
        this.updatedAt = now;
    }

    public void changeSortNo(Integer sortNo, Instant now) {
        this.sortNo = sortNo;
        this.updatedAt = now;
    }

    public void changeStatus(CategoryStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }

    public boolean isLevelOne() {
        return parentId == null;
    }

    public boolean isEnabled() {
        return status == CategoryStatus.ENABLED;
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public CategoryStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
