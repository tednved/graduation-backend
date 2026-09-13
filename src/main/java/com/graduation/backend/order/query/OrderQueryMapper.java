package com.graduation.backend.order.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单列表查询（MyBatis-Plus 物理分页）。
 *
 * <p>分页由 {@code PaginationInnerInterceptor} 追加，SQL 里不写 LIMIT。
 * 买卖视角与对端用户的选择都用 {@code <choose>} 固定成字面量分支，不接受调用方传列名。
 */
@Mapper
public interface OrderQueryMapper {

    /**
     * 我的订单。
     *
     * @param side   {@code BUY} 取当前用户作买家的订单、{@code SELL} 取作卖家的订单，只接受这两个值
     * @param status 订单状态，{@code null} 表示不筛选
     */
    IPage<OrderSummaryRow> selectMyOrders(
            IPage<OrderSummaryRow> page,
            @Param("userId") Long userId,
            @Param("side") String side,
            @Param("status") String status);
}
