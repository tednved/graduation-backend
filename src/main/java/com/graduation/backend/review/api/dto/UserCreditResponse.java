package com.graduation.backend.review.api.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 公开信用摘要。
 *
 * <p>{@code distribution} 的键是分数、值是可见评价条数，1~5 每档都存在（没有评价时为 0），
 * 客户端不必再判断缺键。
 */
public record UserCreditResponse(
        Long userId,
        BigDecimal averageRating,
        Integer reviewCount,
        Map<Integer, Integer> distribution) {
}
