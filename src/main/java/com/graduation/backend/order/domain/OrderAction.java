package com.graduation.backend.order.domain;

/**
 * 订单动作（契约 §4）。
 *
 * <p>{@code CREATE} 只出现在订单事件里（创建时没有前置状态），不会出现在详情的
 * {@code allowedActions}；其余动作按「当前状态 + 当前用户身份」计算。
 */
public enum OrderAction {

    CREATE,
    CONFIRM,
    REJECT,
    CANCEL,
    DELIVER,
    RECEIVE
}
