package com.graduation.backend.review.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建评价。
 *
 * <p>不接收 {@code revieweeId}：对端由服务端按订单推导，避免给任意用户刷分。
 *
 * <p>{@code version} 可选。提供时与订单当前版本比对，不一致说明看到的是旧订单，返回冲突；
 * 不提供则不做版本校验。
 */
public record CreateReviewRequest(

        @NotNull(message = "订单 ID 不能为空")
        Long orderId,

        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分最低 1 分")
        @Max(value = 5, message = "评分最高 5 分")
        Integer rating,

        @Size(max = 500, message = "评价内容最多 500 字")
        String content,

        @Min(value = 0, message = "版本号不能为负")
        Integer version) {
}
