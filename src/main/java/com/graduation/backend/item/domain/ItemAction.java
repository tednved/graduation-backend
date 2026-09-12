package com.graduation.backend.item.domain;

/**
 * 契约 {@code ItemDetail.allowedActions} 的取值。
 *
 * <p>只用于前端展示按钮，后端每次处理写请求时仍然重新校验；因此这里多给或漏给都不会放宽鉴权。
 */
public enum ItemAction {
    EDIT,
    PUBLISH,
    OFF_SHELF,
    DELETE,
    FAVORITE,
    BUY
}
