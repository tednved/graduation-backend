package com.graduation.backend.item.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品列表查询（MyBatis-Plus 物理分页）。
 *
 * <p>分页参数由 {@code PaginationInnerInterceptor} 追加，SQL 里不写 LIMIT，
 * 避免「先查全表再内存分页」。排序值在服务层按白名单校验，这里用 {@code <choose>} 硬编码，
 * 不接受任意列名。
 */
@Mapper
public interface ItemQueryMapper {

    /** 公开搜索：只返回 {@code ON_SALE}，卡片字段 + 首图。 */
    IPage<ItemCardRow> searchCards(
            IPage<ItemCardRow> page,
            @Param("keyword") String keyword,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("condition") String condition,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("sort") String sort);

    /** 我的发布：返回当前用户全部状态，可按状态筛选。 */
    IPage<ItemCardRow> selectMyItems(
            IPage<ItemCardRow> page,
            @Param("sellerId") Long sellerId,
            @Param("status") String status);

    /**
     * 把搜索传入的 {@code categoryId} 展开成「可售二级分类 ID 集合」。
     *
     * <p>契约：传一级分类时包含其启用子分类。放在 SQL 里做，避免每次搜索把整个分类字典读进内存；
     * 返回空集合表示该分类不存在或没有可用子分类，调用方据此直接返回空页（不能把空集合当成不过滤）。
     */
    List<Long> findSellableCategoryIds(@Param("categoryId") Long categoryId);
}
