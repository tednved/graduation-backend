package com.graduation.backend.admin.api;

import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.item.api.dto.AdminOffShelfRequest;
import com.graduation.backend.item.api.dto.ItemDetailResponse;
import com.graduation.backend.item.application.ItemApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端商品操作。
 *
 * <p>只放本里程碑范围内的「强制下架」；{@code GET /admin/items}（{@code listAdminItems}）属于
 * BE-MVP-03，本次不实现。
 *
 * <p>管理员身份在 ApplicationService 的写事务内用 {@code UserService.requireAdmin} 判定并抛
 * {@code BusinessException(AUTH_FORBIDDEN)}——与既有约定一致，不引入 {@code @PreAuthorize}，
 * 也不改 {@code SecurityConfig} 的鉴权结构。放在事务内的原因是授权判断必须与写入同一事务重新读库，
 * 否则降权会在一次请求内被漏掉。
 */
@RestController
@RequestMapping("/api/v1/admin/items")
public class AdminItemController {

    private final ItemApplicationService itemApplicationService;
    private final CurrentUserService currentUserService;

    public AdminItemController(ItemApplicationService itemApplicationService,
                              CurrentUserService currentUserService) {
        this.itemApplicationService = itemApplicationService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{id}/off-shelf")
    public ApiResponse<ItemDetailResponse> offShelf(@PathVariable("id") Long id,
                                                    @Valid @RequestBody AdminOffShelfRequest request) {
        return ApiResponse.ok(itemApplicationService.adminOffShelf(
                currentUserService.requireCurrentUserId(), id, request.reason()));
    }
}
