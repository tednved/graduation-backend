package com.graduation.backend.item.domain;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 金额输入解析。
 *
 * <p>契约里金额是「两位小数的十进制字符串」（{@code ^(0|[1-9][0-9]*)\.[0-9]{2}$}），
 * 全链路不使用浮点：请求字符串直接构造 {@link BigDecimal}，不经过 {@code double}。
 *
 * <p>格式由请求 DTO 上的 {@code @Pattern} 拦下；这里再解析一次，是为了兜住服务内部调用
 * （以及把「超出数据库 DECIMAL(10,2) 可存范围」这类数据库层才会暴露的问题提前变成 400）。
 */
public final class Money {

    /** 契约 {@code MoneyAmount} 的格式，供请求 DTO 的 {@code @Pattern} 直接引用。 */
    public static final String PATTERN_STR = "^(0|[1-9][0-9]*)\\.[0-9]{2}$";

    /** {@code DECIMAL(10,2)} 能表示的上限；超出只能靠数据库报错，故提前拦截。 */
    public static final BigDecimal MAX = new BigDecimal("99999999.99");

    private static final Pattern PATTERN = Pattern.compile(PATTERN_STR);

    private Money() {
    }

    /** 解析必填金额。 */
    public static BigDecimal parse(String raw, String field) {
        if (raw == null || !PATTERN.matcher(raw).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + " 必须是两位小数的金额字符串");
        }
        BigDecimal value = new BigDecimal(raw);
        if (value.compareTo(MAX) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + " 超出可接受范围");
        }
        return value;
    }

    /** 解析可选金额（查询参数的价格区间边界、商品原价）；未提供时为 {@code null}。 */
    public static BigDecimal parseOptional(String raw, String field) {
        return raw == null ? null : parse(raw, field);
    }
}
