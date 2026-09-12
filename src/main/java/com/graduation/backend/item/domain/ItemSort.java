package com.graduation.backend.item.domain;

/**
 * 契约 {@code ItemSort}：搜索排序白名单。
 *
 * <p>用枚举接收查询参数，非法值由框架直接判 400，不需要在服务里手写字符串白名单校验。
 * 真正的 ORDER BY 在 {@code ItemQueryMapper.xml} 里按枚举名硬编码，不接受任意列名。
 */
public enum ItemSort {
    NEWEST,
    PRICE_ASC,
    PRICE_DESC,
    POPULAR
}
