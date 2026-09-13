package com.graduation.backend.order.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 订单写入边界（JPA）。
 *
 * <p>列表分页走 MyBatis-Plus，这里只承担写路径与按主键读取。
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 幂等键查询：同一买家的同一 {@code clientRequestId} 最多一个订单。
     *
     * <p>调用前买家行已被悲观锁定，因此「查不到就插入」不会出现两个事务各插一条。
     */
    Optional<Order> findByBuyerIdAndClientRequestId(Long buyerId, String clientRequestId);

    /** 写操作前锁定订单行，避免并发接单/取消/收货交叉。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
