package com.graduation.backend.order.domain;

/**
 * 交易方式（契约 §4）。
 *
 * <p>MVP 只接受 {@link #OFFLINE}；{@link #SIMULATED_PAYMENT} 保留在领域枚举中，
 * 不作为已支持流程——创建订单时传它会按参数校验失败处理。
 */
public enum TradeMode {

    OFFLINE,
    SIMULATED_PAYMENT;

    public boolean isSupported() {
        return this == OFFLINE;
    }
}
