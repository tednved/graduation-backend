package com.graduation.backend.item.domain;

/**
 * §4 ItemStatus 枚举。
 *
 * <p>状态迁移只允许在 {@link Item} 的领域方法中进行，控制器与查询不得直接改字段。
 * 这里把「可见性 / 可编辑性 / 可收藏性」的判定集中到枚举，避免同一规则在多个服务里各写一遍。
 */
public enum ItemStatus {

    DRAFT,
    ON_SALE,
    RESERVED,
    SOLD,
    OFF_SHELF,
    DELETED;

    /** 契约：公开可见 DRAFT/DELETED 以外的状态。 */
    public boolean isPubliclyVisible() {
        return this != DRAFT && this != DELETED;
    }

    /** 只有草稿与已下架商品可以修改或删除。 */
    public boolean isEditableBySeller() {
        return this == DRAFT || this == OFF_SHELF;
    }

    /** 只有上架中的商品可以主动下架。 */
    public boolean isSellable() {
        return this == ON_SALE;
    }
}
