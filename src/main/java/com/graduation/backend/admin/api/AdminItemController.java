package com.graduation.backend.admin.api;

import com.graduation.backend.admin.api.dto.AdminItemResponse;
import com.graduation.backend.admin.application.AdminItemQueryService;
import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.item.api.dto.AdminOffShelfRequest;
import com.graduation.backend.item.api.dto.ItemDetailResponse;
import com.graduation.backend.item.application.ItemApplicationService;
import com.graduation.backend.item.domain.ItemStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端商品接口。
 *
 * <p>包含强制下架与商品分页查询。
 *
 * <p>管理员身份在 ApplicationService 的写事务内用 {@code UserService.requireAdmin} 判定并抛
 * {@code BusinessException(AUTH_FORBIDDEN)}——与既有约定一致，不引入 {@code @PreAuthorize}，
 * 也不改 {@code SecurityConfig} 的鉴权结构。放在事务内的原因是授权判断必须与写入同一事务重新读库，
 * 否则降权会在一次请求内被漏掉。
 */
@RestController
@RequestMapping("/api/v1/admin/items")
@Validated
public class AdminItemController {

    private final ItemApplicationService itemApplicationService;
    private final AdminItemQueryService adminItemQueryService;
    private final CurrentUserService currentUserService;

    public AdminItemController(ItemApplicationService itemApplicationService,
                               AdminItemQueryService adminItemQueryService,
                               CurrentUserService currentUserService) {
        this.itemApplicationService = itemApplicationService;
        this.adminItemQueryService = adminItemQueryService;
        this.currentUserService = currentUserService;
    }

    /** 商品分页：不受 {@code ON_SALE} 限制，支持多状态、卖家与关键字筛选。 */
    @GetMapping
    public ApiResponse<PageResult<AdminItemResponse>> list(
            @RequestParam(required = false) List<ItemStatus> status,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) @Size(min = 1, max = 50) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(adminItemQueryService.list(
                currentUserService.requireCurrentUserId(), status, sellerId, keyword, page, size));
    }

    @PostMapping("/{id}/off-shelf")
    public ApiResponse<ItemDetailResponse> offShelf(@PathVariable("id") Long id,
                                                    @Valid @RequestBody AdminOffShelfRequest request) {
        return ApiResponse.ok(itemApplicationService.adminOffShelf(
                currentUserService.requireCurrentUserId(), id, request.reason()));
    }
}
