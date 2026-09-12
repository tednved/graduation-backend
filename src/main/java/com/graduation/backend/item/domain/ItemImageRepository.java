package com.graduation.backend.item.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {

    List<ItemImage> findByItemIdOrderBySortNoAsc(Long itemId);

    List<ItemImage> findByItemIdInOrderBySortNoAsc(Collection<Long> itemIds);

    /** 全量替换图片时先清空旧行；调用方需要显式 flush，避免与后续插入撞 (item_id, sort_no) 唯一键。 */
    @Modifying
    @Query("delete from ItemImage i where i.itemId = :itemId")
    void deleteByItemId(@Param("itemId") Long itemId);
}
