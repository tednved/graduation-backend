package com.graduation.backend.favorite.api;

import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.favorite.api.dto.FavoriteStatusResponse;
import com.graduation.backend.favorite.application.FavoriteApplicationService;
import com.graduation.backend.favorite.application.FavoriteQueryService;
import com.graduation.backend.item.api.dto.ItemCardResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口。
 *
 * <p>类级路径只到 {@code /api/v1}：本控制器同时覆盖 {@code /items/{itemId}/favorite*} 与
 * {@code /users/me/favorites}。
 *
 * <p>安全配置里只有单段的 {@code /api/v1/items} 与 {@code /api/v1/items/*} 是匿名可读的，
 * 本控制器的 {@code favorite-status} 是两段路径，仍然要求登录——这一点必须保持。
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class FavoriteController {

    private final FavoriteApplicationService favoriteApplicationService;
    private final FavoriteQueryService favoriteQueryService;
    private final CurrentUserService currentUserService;

    public FavoriteController(FavoriteApplicationService favoriteApplicationService,
                             FavoriteQueryService favoriteQueryService,
                             CurrentUserService currentUserService) {
        this.favoriteApplicationService = favoriteApplicationService;
        this.favoriteQueryService = favoriteQueryService;
        this.currentUserService = currentUserService;
    }

    /** 收藏商品；幂等，重复调用同样返回 204。 */
    @PutMapping("/items/{itemId}/favorite")
    public ResponseEntity<Void> add(@PathVariable("itemId") Long itemId) {
        favoriteApplicationService.add(currentUserId(), itemId);
        return ResponseEntity.noContent().build();
    }

    /** 取消收藏；本就未收藏同样返回 204。 */
    @DeleteMapping("/items/{itemId}/favorite")
    public ResponseEntity<Void> remove(@PathVariable("itemId") Long itemId) {
        favoriteApplicationService.remove(currentUserId(), itemId);
        return ResponseEntity.noContent().build();
    }

    /** 查询当前用户对某商品的收藏状态。 */
    @GetMapping("/items/{itemId}/favorite-status")
    public ApiResponse<FavoriteStatusResponse> status(@PathVariable("itemId") Long itemId) {
        return ApiResponse.ok(favoriteApplicationService.status(currentUserId(), itemId));
    }

    /** 我的收藏：商品卡片分页。 */
    @GetMapping("/users/me/favorites")
    public ApiResponse<PageResult<ItemCardResponse>> myFavorites(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(favoriteQueryService.myFavorites(page, size, currentUserId()));
    }

    private Long currentUserId() {
        return currentUserService.requireCurrentUserId();
    }
}
