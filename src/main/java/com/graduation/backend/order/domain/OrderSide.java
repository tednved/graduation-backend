package com.graduation.backend.order.domain;

/**
 * 我的订单的买卖视角（契约层枚举，不是领域状态）。
 *
 * <p>{@code BUY} 返回当前用户作为买家的订单，{@code SELL} 返回作为卖家的订单；
 * 同一路径上用它区分两个列表，避免再开一条语义重复的接口。
 */
public enum OrderSide {

    BUY,
    SELL
}
