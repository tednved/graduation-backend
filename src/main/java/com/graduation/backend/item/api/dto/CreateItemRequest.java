package com.graduation.backend.item.api.dto;

import com.graduation.backend.item.domain.ItemCondition;
import com.graduation.backend.item.domain.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建商品草稿。
 *
 * <p>金额用字符串接收（契约 {@code MoneyAmount} 就是两位小数字符串），
 * 由 {@link Money#parse} 转成 {@link java.math.BigDecimal}，全程不经过浮点。
 *
 * <p>请求里没有 {@code campusId}：单校区 MVP 由服务端绑定 {@code code=MAIN} 的校区。
 * {@code condition} 对应库列 {@code condition_level}。
 */
public record CreateItemRequest(
        @NotBlank(message = "标题不能为空")
        @Size(min = 2, max = 80, message = "标题长度需在 2~80 之间")
        String title,

        @NotBlank(message = "描述不能为空")
        @Size(min = 10, max = 2000, message = "描述长度需在 10~2000 之间")
        String description,

        @NotBlank(message = "价格不能为空")
        @Pattern(regexp = Money.PATTERN_STR, message = "价格必须是两位小数的金额字符串")
        String price,

        @Pattern(regexp = Money.PATTERN_STR, message = "原价必须是两位小数的金额字符串")
        String originalPrice,

        @NotNull(message = "商品成色不能为空")
        ItemCondition condition,

        @NotNull(message = "分类不能为空")
        Long categoryId,

        @NotEmpty(message = "至少上传一张商品图片")
        @Size(min = 1, max = 9, message = "商品图片数量需在 1~9 之间")
        List<@NotNull(message = "图片 ID 不能为空") Long> imageFileIds) {
}
