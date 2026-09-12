package com.graduation.backend.item.domain;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 二手商品聚合根。
 *
 * <p>状态只能经本类的方法变更（{@code update}/{@code publish}/{@code offShelf}/{@code reserve}/
 * {@code release}/{@code markSold}/{@code delete}），控制器与查询服务不得直接改字段。
 *
 * <p>{@code viewCount}、{@code favoriteCount} 标了 {@code insertable=false, updatable=false}：
 * 它们只由 {@code ItemRepository} 的原子 {@code UPDATE ... SET x = x + 1} 维护。若可写，
 * Hibernate 在刷实体 UPDATE 时会把内存里的旧计数一并写回，并发下的收藏/浏览量增量会被静默覆盖。
 *
 * <p>{@code @Version} 提供乐观锁：编辑必须携带当前版本，过期版本在提交时被数据库拒绝。
 */
@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "title", nullable = false, length = 80)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_level", nullable = false, length = 32)
    private ItemCondition conditionLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ItemStatus status;

    @Column(name = "admin_lock", nullable = false)
    private boolean adminLock;

    @Column(name = "off_shelf_reason", length = 200)
    private String offShelfReason;

    @Column(name = "view_count", nullable = false, insertable = false, updatable = false)
    private Integer viewCount = 0;

    @Column(name = "favorite_count", nullable = false, insertable = false, updatable = false)
    private Integer favoriteCount = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Item() {
    }

    private Item(Long sellerId, Long categoryId, Long campusId, String title, String description,
                 BigDecimal price, BigDecimal originalPrice, ItemCondition condition, Instant now) {
        this.sellerId = sellerId;
        this.categoryId = categoryId;
        this.campusId = campusId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.originalPrice = originalPrice;
        this.conditionLevel = condition;
        this.status = ItemStatus.DRAFT;
        this.adminLock = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 创建草稿。校区由服务层按 {@code code=MAIN} 绑定，请求不接受校区字段。 */
    public static Item create(Long sellerId, Long categoryId, Long campusId, String title, String description,
                              BigDecimal price, BigDecimal originalPrice, ItemCondition condition, Instant now) {
        return new Item(sellerId, categoryId, campusId, title, description, price, originalPrice, condition, now);
    }

    /** 全量替换可编辑字段。仅草稿/已下架且未被管理员锁定时允许。 */
    public void update(String title, String description, BigDecimal price, BigDecimal originalPrice,
                       ItemCondition condition, Long categoryId, Instant now) {
        requireEditable();
        this.title = title;
        this.description = description;
        this.price = price;
        this.originalPrice = originalPrice;
        this.conditionLevel = condition;
        this.categoryId = categoryId;
        this.updatedAt = now;
    }

    /** 从草稿或已下架进入在售；首次上架设置 {@code publishedAt}。 */
    public void publish(Instant now) {
        requireEditable();
        this.status = ItemStatus.ON_SALE;
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
        this.updatedAt = now;
    }

    /** 卖家主动下架。仅 {@code ON_SALE} 可下架，{@code RESERVED}/{@code SOLD} 不可。 */
    public void offShelf(Instant now) {
        if (status != ItemStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.ITEM_NOT_EDITABLE, "当前状态不允许下架");
        }
        this.status = ItemStatus.OFF_SHELF;
        this.updatedAt = now;
    }

    /** 管理员强制下架：置 {@code OFF_SHELF} 并锁定，记录原因。锁定后卖家不可修改或重上架。 */
    public void adminOffShelf(String reason, Instant now) {
        this.status = ItemStatus.OFF_SHELF;
        this.adminLock = true;
        this.offShelfReason = reason;
        this.updatedAt = now;
    }

    /** 下单成功后预留商品。 */
    public void reserve(Instant now) {
        if (status != ItemStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE, "商品当前不可购买");
        }
        this.status = ItemStatus.RESERVED;
        this.updatedAt = now;
    }

    /** 订单取消/拒单后释放回在售。 */
    public void release(Instant now) {
        if (status != ItemStatus.RESERVED) {
            throw new BusinessException(ErrorCode.ITEM_NOT_EDITABLE, "商品当前不处于预留状态");
        }
        this.status = ItemStatus.ON_SALE;
        this.updatedAt = now;
    }

    /** 买家确认收货后标记售出。 */
    public void markSold(Instant now) {
        if (status != ItemStatus.RESERVED) {
            throw new BusinessException(ErrorCode.ITEM_NOT_EDITABLE, "商品当前不处于预留状态");
        }
        this.status = ItemStatus.SOLD;
        this.updatedAt = now;
    }

    /**
     * 删除是状态变更，不物理删除。
     *
     * <p>管理员锁定的商品仍允许卖家删除（契约只禁止「修改或重上架」），删除后不再出现在任何列表。
     */
    public void delete(Instant now) {
        if (!status.isEditableBySeller()) {
            throw new BusinessException(ErrorCode.ITEM_NOT_EDITABLE, "当前状态不允许删除");
        }
        this.status = ItemStatus.DELETED;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(Long userId) {
        return sellerId != null && sellerId.equals(userId);
    }

    /** 契约：公开可见 DRAFT/DELETED 以外的状态；DRAFT/DELETED 仅本人或管理员可见。 */
    public boolean isVisibleTo(Long viewerId, boolean viewerIsAdmin) {
        if (status.isPubliclyVisible()) {
            return true;
        }
        return viewerIsAdmin || isOwnedBy(viewerId);
    }

    private void requireEditable() {
        if (!status.isEditableBySeller()) {
            throw new BusinessException(ErrorCode.ITEM_NOT_EDITABLE, "当前状态不允许编辑");
        }
        if (adminLock) {
            throw new BusinessException(ErrorCode.ITEM_NOT_EDITABLE, "商品已被管理员锁定，不能修改或重新上架");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getCampusId() {
        return campusId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public ItemCondition getConditionLevel() {
        return conditionLevel;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public boolean isAdminLock() {
        return adminLock;
    }

    public String getOffShelfReason() {
        return offShelfReason;
    }

    public Integer getViewCount() {
        return viewCount == null ? 0 : viewCount;
    }

    public Integer getFavoriteCount() {
        return favoriteCount == null ? 0 : favoriteCount;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
