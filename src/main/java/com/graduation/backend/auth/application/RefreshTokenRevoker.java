package com.graduation.backend.auth.application;

import com.graduation.backend.auth.domain.RefreshToken;
import com.graduation.backend.auth.domain.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Refresh Token 的批量撤销。
 *
 * <p>重放检测必须撤销成功之后再返回 401，而调用方随后抛异常会回滚自己的事务，
 * 因此这里用 {@link Propagation#REQUIRES_NEW} 独立提交，保证撤销结果不会被回滚吃掉。
 */
@Service
public class RefreshTokenRevoker {

    private final RefreshTokenRepository repository;
    private final Clock clock;

    public RefreshTokenRevoker(RefreshTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForDevice(Long userId, String deviceId) {
        Instant now = clock.instant();
        List<RefreshToken> active = repository.findByUserIdAndDeviceIdAndRevokedAtIsNull(userId, deviceId);
        active.forEach(token -> token.revoke(now));
    }
}
