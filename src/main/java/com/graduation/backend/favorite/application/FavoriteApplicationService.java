package com.graduation.backend.favorite.application;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.favorite.api.dto.FavoriteStatusResponse;
import com.graduation.backend.favorite.domain.Favorite;
import com.graduation.backend.favorite.domain.FavoriteRepository;
import com.graduation.backend.item.domain.Item;
import com.graduation.backend.item.domain.ItemRepository;
import com.graduation.backend.user.application.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 收藏写路径。
 *
 * <p>幂等：重复收藏、重复取消都成功返回，不报冲突。并发下同一用户的收藏操作会被
 * {@code requireActiveUserForUpdate} 悲观锁串行化，使「查有没有 → 插入」不会两个事务都查不到
 * 而各插一行（{@code uk_favorites_user_item} 仍是最后一道防线）。
 *
 * <p>{@code items.favorite_count} 用原子 SQL 增减，且只在真的插入/删除成功后调整，
 * 不用实体字段赋值，避免并发覆盖；减法是 {@code GREATEST(x - 1, 0)}，不会出现负数。
 *
 * <p>「不能收藏自己商品」按契约是 409 {@code ITEM_SELF_OPERATION}（不是 403）。
 */
@Service
public class FavoriteApplicationService {

    private final FavoriteRepository favoriteRepository;
    private final ItemRepository itemRepository;
    private final UserService userService;
    private final Clock clock;

    public FavoriteApplicationService(FavoriteRepository favoriteRepository,
                                      ItemRepository itemRepository,
                                      UserService userService,
                                      Clock clock) {
        this.favoriteRepository = favoriteRepository;
        this.itemRepository = itemRepository;
        this.userService = userService;
        this.clock = clock;
    }

    /** 收藏。已收藏直接成功返回，不重复加计数。 */
    @Transactional
    public void add(Long userId, Long itemId) {
        userService.requireActiveUserForUpdate(userId);
        Item item = requireFavoritableItem(itemId);
        if (item.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.ITEM_SELF_OPERATION, "不能收藏自己发布的商品");
        }
        if (favoriteRepository.existsByUserIdAndItemId(userId, itemId)) {
            return;
        }
        favoriteRepository.save(Favorite.of(userId, itemId, clock.instant()));
        itemRepository.incrementFavoriteCount(itemId);
    }

    /** 取消收藏。不存在收藏关系也成功返回（幂等），此时不扣减计数。 */
    @Transactional
    public void remove(Long userId, Long itemId) {
        userService.requireActiveUserForUpdate(userId);
        requireFavoritableItem(itemId);
        int deleted = favoriteRepository.deleteByUserIdAndItemId(userId, itemId);
        if (deleted > 0) {
            itemRepository.decrementFavoriteCount(itemId);
        }
    }

    /** 当前用户对某商品的收藏状态。 */
    @Transactional(readOnly = true)
    public FavoriteStatusResponse status(Long userId, Long itemId) {
        userService.requireActiveUser(userId);
        requireFavoritableItem(itemId);
        return new FavoriteStatusResponse(favoriteRepository.existsByUserIdAndItemId(userId, itemId));
    }

    /**
     * 收藏前置：商品必须存在且不是 {@code DRAFT}/{@code DELETED}。
     *
     * <p>不可收藏的商品一律 404 {@code RESOURCE_NOT_FOUND}，与契约
     * 「资源不存在或对当前用户不可见」的 404 口径一致，也让三个收藏接口行为一致。
     */
    private Item requireFavoritableItem(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"));
        if (!item.getStatus().isPubliclyVisible()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在");
        }
        return item;
    }
}
