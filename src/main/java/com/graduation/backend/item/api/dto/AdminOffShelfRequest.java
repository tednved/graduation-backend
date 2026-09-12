package com.graduation.backend.item.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员强制下架。原因必填、2～200 字，会写入卖家消息与审计日志。 */
public record AdminOffShelfRequest(
        @NotBlank(message = "强制下架原因不能为空")
        @Size(min = 2, max = 200, message = "强制下架原因长度需在 2~200 之间")
        String reason) {
}
