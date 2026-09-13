package com.graduation.backend.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** 订单快照仓库。快照只追加一条，没有更新或删除路径。 */
public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, Long> {
}
