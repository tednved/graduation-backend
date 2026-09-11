package com.graduation.backend.category.domain;

import com.graduation.backend.common.domain.CategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByParentIdIsNullOrderBySortNoAscIdAsc();

    List<Category> findByParentIdOrderBySortNoAscIdAsc(Long parentId);

    List<Category> findByStatusAndParentIdIsNullOrderBySortNoAscIdAsc(CategoryStatus status);

    List<Category> findByStatusAndParentIdInOrderBySortNoAscIdAsc(CategoryStatus status, Collection<Long> parentIds);

    List<Category> findByParentIdInOrderBySortNoAscIdAsc(Collection<Long> parentIds);

    List<Category> findByParentIdAndStatusOrderBySortNoAscIdAsc(Long parentId, CategoryStatus status);

    Optional<Category> findByParentIdAndName(Long parentId, String name);

    Optional<Category> findByParentIdIsNullAndName(String name);

    /**
     * 统计会阻止分类停用的商品数。
     *
     * <p>商品表不属于分类模块，这里刻意用原生计数查询而不是引入商品实体，
     * 避免为了一个停用校验把商品领域模型提前搬进来。
     */
    @Query(value = "SELECT COUNT(*) FROM items WHERE category_id IN (:categoryIds) "
            + "AND status IN ('ON_SALE', 'RESERVED')", nativeQuery = true)
    long countBlockingItems(@Param("categoryIds") Collection<Long> categoryIds);
}
