package com.graduation.backend.item.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 商品的写入边界（JPA）。
 *
 * <p>{@code viewCount}/{@code favoriteCount} 一律用原生原子 SQL 增减：实体映射把这两列标成
 * {@code insertable=false, updatable=false}，因此并发的计数增量不会被 Hibernate 刷实体时覆盖。
 */
public interface ItemRepository extends JpaRepository<Item, Long> {

    /** 写操作前锁定商品行，避免并发编辑/上架与下架交叉。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE items SET view_count = view_count + 1 WHERE id = :id", nativeQuery = true)
    int incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE items SET favorite_count = favorite_count + 1 WHERE id = :id", nativeQuery = true)
    int incrementFavoriteCount(@Param("id") Long id);

    /** 收藏计数减一且不小于 0，避免历史脏数据把计数压成负数。 */
    @Modifying
    @Query(value = "UPDATE items SET favorite_count = GREATEST(favorite_count - 1, 0) WHERE id = :id",
            nativeQuery = true)
    int decrementFavoriteCount(@Param("id") Long id);

    /**
     * 是否存在进行中的订单。
     *
     * <p>订单领域属于 BE-08，这里刻意用原生计数而不是引入订单实体：删除商品只需要知道
     * 「能不能删」，不需要订单聚合。
     */
    @Query(value = "SELECT COUNT(*) FROM orders WHERE item_id = :itemId "
            + "AND status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'PENDING_RECEIPT')", nativeQuery = true)
    long countInProgressOrders(@Param("itemId") Long itemId);
}
