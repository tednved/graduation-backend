package com.graduation.backend.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 订单事件仓库。事件只追加，读取按时间线顺序。 */
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    /**
     * 订单时间线。
     *
     * <p>{@code created_at} 精度是毫秒，同一毫秒内的多条事件用自增 ID 兜底，
     * 保证时间线稳定且与写入顺序一致。
     */
    List<OrderEvent> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
