package com.graduation.backend.item.api;

import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.item.api.dto.ItemCardResponse;
import com.graduation.backend.item.application.ItemQueryService;
import com.graduation.backend.item.domain.ItemCondition;
import com.graduation.backend.item.domain.ItemSort;
import com.graduation.backend.item.domain.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开商品搜索。
 *
 * <p>契约里本接口 {@code security: []}（匿名可访问），因此单独成一个控制器：
 * 路径 {@code /api/v1/items} 已加入安全白名单，仅这一个单段路径公开，
 * {@code /api/v1/items/**} 下的个性化接口仍需登录。
 *
 * <p>非法排序值/成色由枚举反序列化直接判 400；未知分类返回空页（本接口没有 404）。
 */
@RestController
@RequestMapping("/api/v1/items")
@Validated
public class ItemSearchController {

    private final ItemQueryService itemQueryService;

    public ItemSearchController(ItemQueryService itemQueryService) {
        this.itemQueryService = itemQueryService;
    }

    @GetMapping
    public ApiResponse<PageResult<ItemCardResponse>> search(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(min = 1, max = 50) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ItemCondition condition,
            @RequestParam(required = false) @Pattern(regexp = Money.PATTERN_STR, message = "minPrice 必须是两位小数的金额字符串")
            String minPrice,
            @RequestParam(required = false) @Pattern(regexp = Money.PATTERN_STR, message = "maxPrice 必须是两位小数的金额字符串")
            String maxPrice,
            @RequestParam(defaultValue = "NEWEST") ItemSort sort) {
        return ApiResponse.ok(itemQueryService.search(
                page, size, keyword, categoryId, condition,
                Money.parseOptional(minPrice, "minPrice"),
                Money.parseOptional(maxPrice, "maxPrice"),
                sort));
    }
}
