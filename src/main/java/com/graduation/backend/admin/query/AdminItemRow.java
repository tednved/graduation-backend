package com.graduation.backend.admin.query;

import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.domain.UserStatus;
import com.graduation.backend.item.domain.ItemStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 管理端商品行（MyBatis 直接映射）。
 *
 * <p>不受 {@code ON_SALE} 限制：管理端要看得到草稿、下架、已售与已删除的全部商品。
 */
public class AdminItemRow {

    private Long id;
    private String title;
    private BigDecimal price;
    private ItemStatus status;
    private Long sellerId;
    private String sellerNickname;
    private String sellerAvatarUrl;
    private Long categoryId;
    private String categoryName;
    private Long campusId;
    private String campusName;
    private Boolean adminLock;
    private String offShelfReason;
    private Integer favoriteCount;
    private Integer viewCount;
    private Instant createdAt;
    private Instant updatedAt;

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

    public ItemStatus getStatus() {
        return status;
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    public String getSellerNickname() {
        return sellerNickname;
    }

    public void setSellerNickname(String sellerNickname) {
        this.sellerNickname = sellerNickname;
    }

    public String getSellerAvatarUrl() {
        return sellerAvatarUrl;
    }

    public void setSellerAvatarUrl(String sellerAvatarUrl) {
        this.sellerAvatarUrl = sellerAvatarUrl;
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
}
