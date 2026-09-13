package com.graduation.backend.admin.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 管理端全站订单分页。 */
@Mapper
public interface AdminOrderQueryMapper {
    IPage<AdminOrderRow> selectOrders(
            IPage<AdminOrderRow> page,
            @Param("status") String status,
            @Param("buyerId") Long buyerId,
            @Param("sellerId") Long sellerId,
            @Param("keyword") String keyword);
}
