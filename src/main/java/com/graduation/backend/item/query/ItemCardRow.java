package com.graduation.backend.item.query;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 商品列表查询行。
 *
 * <p>刻意不是 JPA 实体：列表/搜索走 MyBatis-Plus（条件筛选 + 物理分页投影），
 * 写入走 JPA。两者作用在同一张表但从不同时更新同一行。
 *
 * <p>同时承载三种列表（搜索卡片、我的发布、收藏列表），因此字段是三者并集，
 * 每条 SQL 只 SELECT 自己需要的列，其余保持 {@code null}。
 */
public class ItemCardRow {

    private Long id;
    private String title;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String conditionLevel;
    private String status;
    private String coverImageUrl;
    private Long campusId;
    private String campusName;
    private Long categoryId;
    private String categoryName;
    private Integer favoriteCount;
    private Integer viewCount;
    private Boolean adminLock;
    private String offShelfReason;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public String getConditionLevel() {
        return conditionLevel;
    }

    public void setConditionLevel(String conditionLevel) {
        this.conditionLevel = conditionLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public Long getCampusId() {
        return campusId;
    }

    public void setCampusId(Long campusId) {
        this.campusId = campusId;
    }

    public String getCampusName() {
        return campusName;
    }

    public void setCampusName(String campusName) {
        this.campusName = campusName;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(Integer favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Boolean getAdminLock() {
        return adminLock;
    }

    public void setAdminLock(Boolean adminLock) {
        this.adminLock = adminLock;
    }

    public String getOffShelfReason() {
        return offShelfReason;
    }

    public void setOffShelfReason(String offShelfReason) {
        this.offShelfReason = offShelfReason;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
