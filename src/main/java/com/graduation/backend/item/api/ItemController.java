package com.graduation.backend.item.api;

import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.item.api.dto.CreateItemRequest;
import com.graduation.backend.item.api.dto.ItemDetailResponse;
import com.graduation.backend.item.api.dto.MyItemSummaryResponse;
import com.graduation.backend.item.api.dto.UpdateItemRequest;
import com.graduation.backend.item.application.ItemApplicationService;
import com.graduation.backend.item.application.ItemQueryService;
import com.graduation.backend.item.application.ItemViewerResolver;
import com.graduation.backend.item.domain.ItemStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口。
 *
 * <p>类级路径只到 {@code /api/v1}：本控制器同时覆盖 {@code /items/**} 与
 * {@code /users/me/items}，用方法级完整路径更直观。
 *
 * <p>控制器只做参数转接：权限判定在服务层事务内重新读库，写入在 ApplicationService，
 * 领域状态只经 {@code Item} 的领域方法变更。
 *
 * <p>详情接口允许匿名（契约 {@code security: [{}, bearerAuth: []]}），
 * 因此查看者用 {@link ItemViewerResolver} 解析成「匿名或某人」，而不是强制登录。
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class ItemController {

    private final ItemApplicationService itemApplicationService;
    private final ItemQueryService itemQueryService;
    private final ItemViewerResolver viewerResolver;
    private final CurrentUserService currentUserService;

    public ItemController(ItemApplicationService itemApplicationService,
                          ItemQueryService itemQueryService,
                          ItemViewerResolver viewerResolver,
                          CurrentUserService currentUserService) {
        this.itemApplicationService = itemApplicationService;
        this.itemQueryService = itemQueryService;
        this.viewerResolver = viewerResolver;
        this.currentUserService = currentUserService;
    }

    /** 创建商品草稿。 */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ItemDetailResponse> create(@Valid @RequestBody CreateItemRequest request) {
        return ApiResponse.ok(itemApplicationService.create(currentUserId(), request));
    }

    /** 公开详情；带 Token 时返回个性化字段。 */
    @GetMapping("/items/{id}")
    public ApiResponse<ItemDetailResponse> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(itemQueryService.detail(id, viewerResolver.resolve()));
    }

    /** 修改商品：省略字段保留原值，{@code version} 必填。 */
    @PutMapping("/items/{id}")
    public ApiResponse<ItemDetailResponse> update(@PathVariable("id") Long id,
                                                  @Valid @RequestBody UpdateItemRequest request) {
        return ApiResponse.ok(itemApplicationService.update(currentUserId(), id, request));
    }

    /** 删除商品：状态变更为 DELETED，不物理删除。 */
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        itemApplicationService.delete(currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /** 上架。 */
    @PostMapping("/items/{id}/publish")
    public ApiResponse<ItemDetailResponse> publish(@PathVariable("id") Long id) {
        return ApiResponse.ok(itemApplicationService.publish(currentUserId(), id));
    }

    /** 主动下架。 */
    @PostMapping("/items/{id}/off-shelf")
    public ApiResponse<ItemDetailResponse> offShelf(@PathVariable("id") Long id) {
        return ApiResponse.ok(itemApplicationService.offShelf(currentUserId(), id));
    }

    /** 我的发布：包含全部状态，可按状态筛选。 */
    @GetMapping("/users/me/items")
    public ApiResponse<PageResult<MyItemSummaryResponse>> myItems(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) ItemStatus status) {
        return ApiResponse.ok(itemQueryService.myItems(page, size, currentUserId(), status));
    }

    private Long currentUserId() {
        return currentUserService.requireCurrentUserId();
    }
}
