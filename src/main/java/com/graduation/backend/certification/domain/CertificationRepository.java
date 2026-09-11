package com.graduation.backend.certification.domain;

import com.graduation.backend.common.domain.CertificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    Optional<Certification> findFirstByUserIdOrderByIdDesc(Long userId);

    Optional<Certification> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, CertificationStatus status);

    /** 审核时锁定申请行，避免并发重复审核产生两条审核结果。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Certification c where c.id = :id")
    Optional<Certification> findByIdForUpdate(@Param("id") Long id);
}
