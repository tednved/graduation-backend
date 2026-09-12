package com.graduation.backend.favorite.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.favorite.query.FavoriteQueryMapper;
import com.graduation.backend.item.api.dto.ItemCardResponse;
import com.graduation.backend.item.query.ItemCardRow;
import com.graduation.backend.user.application.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收藏列表查询。
 *
 * <p>联表投影交给 {@code FavoriteQueryMapper}（MyBatis-Plus 物理分页），与搜索列表共用
 * {@code ItemCard} 投影，保证两个列表的卡片字段口径一致。
 *
 * <p>失效商品（已下架/已售/已删除）仍会返回，并带上各自的 {@code status}：
 * 契约把呈现方式交给前端决策，接口过滤掉的话前端无法展示「已失效」。
 */
@Service
public class FavoriteQueryService {

    private final FavoriteQueryMapper favoriteQueryMapper;
    private final UserService userService;

    public FavoriteQueryService(FavoriteQueryMapper favoriteQueryMapper, UserService userService) {
        this.favoriteQueryMapper = favoriteQueryMapper;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public PageResult<ItemCardResponse> myFavorites(int page, int size, Long userId) {
        userService.requireActiveUser(userId);
        IPage<ItemCardRow> result = favoriteQueryMapper.selectFavoriteItems(new Page<>(page + 1L, size), userId);
        return PageResult.of(page, size, result.getTotal(),
                result.getRecords().stream().map(ItemCardResponse::from).toList());
    }
}
