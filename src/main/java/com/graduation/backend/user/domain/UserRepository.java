package com.graduation.backend.user.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByOpenid(String openid);

    /**
     * 悲观锁定用户行。
     *
     * <p>用于需要「同一用户串行化」的写入（例如同时提交两笔认证申请）：
     * 提交方先锁住用户行，再检查是否已有 PENDING，从而避免两个事务都查不到 PENDING 而各插入一条。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
