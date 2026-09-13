package com.graduation.backend.notification.application;

import com.graduation.backend.notification.domain.NotificationType;

/**
 * 待写入的站内消息。
 *
 * <p>在业务事务内发布、提交后才真正落库（见 {@link NotificationPublisher}），
 * 因此业务回滚不会留下「订单没建成却收到通知」的脏消息。
 */
public record NotificationMessage(
        Long userId,
        NotificationType type,
        String title,
        String content,
        String bizType,
        Long bizId) {

    /** 订单类消息：跳转目标是订单详情。 */
    public static final String BIZ_TYPE_ORDER = "ORDER";

    /** 商品类消息：跳转目标是商品详情。 */
    public static final String BIZ_TYPE_ITEM = "ITEM";
}
