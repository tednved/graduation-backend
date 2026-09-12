package com.graduation.backend.favorite.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    /**
     * 删除收藏关系，返回实际删除的行数。
     *
     * <p>返回行数用于判断「是否真的删掉了」：只有真删掉了才把商品收藏计数减一，
     * 重复取消（0 行）不重复扣减。
     */
    @Modifying
    @Query("delete from Favorite f where f.userId = :userId and f.itemId = :itemId")
    int deleteByUserIdAndItemId(@Param("userId") Long userId, @Param("itemId") Long itemId);
}
