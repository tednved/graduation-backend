package com.graduation.backend.favorite.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.graduation.backend.item.query.ItemCardRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 收藏列表联表查询（MyBatis-Plus 物理分页）。
 *
 * <p>返回 {@code item} 模块的卡片行：收藏列表与搜索列表在契约里都是 {@code ItemCard}，
 * 共用一个投影类型可以保证两处字段口径完全一致。
 */
@Mapper
public interface FavoriteQueryMapper {

    IPage<ItemCardRow> selectFavoriteItems(IPage<ItemCardRow> page, @Param("userId") Long userId);
}
