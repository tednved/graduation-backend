package com.graduation.backend.admin.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 管理端商品查询（MyBatis-Plus 物理分页），不受 {@code ON_SALE} 限制。 */
@Mapper
public interface AdminItemQueryMapper {

    /**
     * 多状态、多卖家与关键字筛选。
     *
     * @param statuses {@code null} 或空表示不筛状态
     * @param keyword  已转义的 LIKE 模式或 {@code null}
     */
    IPage<AdminItemRow> selectItems(
            IPage<AdminItemRow> page,
            @Param("statuses") List<String> statuses,
            @Param("sellerId") Long sellerId,
            @Param("keyword") String keyword);
}
