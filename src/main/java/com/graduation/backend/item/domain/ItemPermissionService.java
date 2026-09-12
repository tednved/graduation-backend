package com.graduation.backend.item.domain;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品可见性、归属与可执行动作判定。
 *
 * <p>纯判定，不读库、不依赖其它服务：调用方必须在自己的事务里先取到商品实体和管理员身份，
 * 再把结果传进来。规则集中在这里，避免「详情放行、写入又拦一次」两套口径。
 *
 * <p>错误码取舍：不可见的商品一律 404 {@code RESOURCE_NOT_FOUND}（契约把 404 定义为
 * 「资源不存在，或对当前用户不可见」）；可见但不属于当前用户时才用 403 {@code AUTH_FORBIDDEN}。
 */
@Service
public class ItemPermissionService {

    /** 详情读取：DRAFT/DELETED 仅本人或管理员可见，其余状态公开。 */
    public void requireVisible(Item item, Long viewerId, boolean viewerIsAdmin) {
        if (!item.isVisibleTo(viewerId, viewerIsAdmin)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在");
        }
    }

    /** 写入类操作（修改/上下架/删除）：先按可见性规则排除不可见商品，再要求是本人。 */
    public void requireSeller(Item item, Long viewerId, boolean viewerIsAdmin) {
        requireVisible(item, viewerId, viewerIsAdmin);
        if (!item.isOwnedBy(viewerId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "只能操作自己发布的商品");
        }
    }

    /**
     * 是否满足下单条件。
     *
     * <p>契约把它定义为「是否满足下单条件」，因此与创建订单的前置保持一致：
     * 非卖家本人、商品在售、且买家已通过校园认证。匿名访问恒为 {@code false}。
     */
    public boolean canBuy(Item item, Long viewerId, boolean viewerCertified) {
        return viewerId != null
                && viewerCertified
                && !item.isOwnedBy(viewerId)
                && item.getStatus() == ItemStatus.ON_SALE;
    }

    /**
     * 当前用户对该商品可执行的操作。
     *
     * <p>卖家：未锁定且在草稿/已下架时可编辑、可上架；在售时可下架；草稿/已下架时可删除。
     * 非卖家：可见商品可收藏；满足下单条件时可购买。匿名返回空数组。
     */
    public List<ItemAction> allowedActions(Item item, Long viewerId, boolean viewerCertified) {
        if (viewerId == null) {
            return List.of();
        }
        List<ItemAction> actions = new ArrayList<>();
        if (item.isOwnedBy(viewerId)) {
            if (item.getStatus().isEditableBySeller() && !item.isAdminLock()) {
                actions.add(ItemAction.EDIT);
                actions.add(ItemAction.PUBLISH);
            }
            if (item.getStatus().isSellable()) {
                actions.add(ItemAction.OFF_SHELF);
            }
            if (item.getStatus().isEditableBySeller()) {
                actions.add(ItemAction.DELETE);
            }
            return List.copyOf(actions);
        }
        if (!item.getStatus().isPubliclyVisible()) {
            return List.of();
        }
        actions.add(ItemAction.FAVORITE);
        if (canBuy(item, viewerId, viewerCertified)) {
            actions.add(ItemAction.BUY);
        }
        return List.copyOf(actions);
    }
}
