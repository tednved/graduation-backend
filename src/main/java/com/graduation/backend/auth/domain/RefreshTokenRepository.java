package com.graduation.backend.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndDeviceIdAndRevokedAtIsNull(Long userId, String deviceId);

    /** 该用户所有设备上仍未撤销的 Refresh Token，供禁用账号时一次性全部撤销。 */
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
}
