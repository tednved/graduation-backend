package com.graduation.backend.order.api.dto;

import com.graduation.backend.order.domain.TradeMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建订单。
 *
 * <p>{@code clientRequestId} 是买家维度的幂等键：客户端在一次下单动作里生成并复用，
 * 网络超时重发同一个值只会得到原订单，不会产生第二笔。
 *
 * <p>{@code tradeMode} 由服务层校验是否受支持（MVP 仅 {@code OFFLINE}），
 * 不支持的值返回参数错误而不是状态冲突。
 */
public record CreateOrderRequest(

        @NotNull(message = "商品 ID 不能为空")
        Long itemId,

        @NotNull(message = "交易方式不能为空")
        TradeMode tradeMode,

        @NotBlank(message = "请求标识不能为空")
        @Size(min = 1, max = 64, message = "请求标识长度需在 1~64 之间")
        String clientRequestId) {
}
