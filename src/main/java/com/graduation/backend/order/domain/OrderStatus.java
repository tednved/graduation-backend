package com.graduation.backend.order.domain;

/**
 * 订单状态（契约 §4）。
 *
 * <p>状态只能经 {@link Order} 的领域方法变更，控制器与查询服务不得直接改字段。
 */
public enum OrderStatus {

    /** 待卖家接单：卖家可接单/拒单，买家可取消。 */
    PENDING_CONFIRMATION,
    /** 已接单：双方可取消，卖家可交付。 */
    CONFIRMED,
    /** 待买家收货：仅买家可确认收货。 */
    PENDING_RECEIPT,
    /** 已完成：商品已售出，双方可评价。 */
    COMPLETED,
    /** 已取消（买家/卖家主动取消）。 */
    CANCELLED,
    /** 已被卖家拒单。 */
    REJECTED
}
