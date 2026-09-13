package com.graduation.backend.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 拒单或取消的原因，契约要求 2～200 字且必填。 */
public record OrderReasonRequest(

        @NotBlank(message = "原因不能为空")
        @Size(min = 2, max = 200, message = "原因长度需在 2~200 之间")
        String reason) {
}
