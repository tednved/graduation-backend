package com.graduation.backend.auth.application;

import com.graduation.backend.auth.api.dto.RefreshTokenRequest;
import com.graduation.backend.auth.api.dto.TokenResponse;
import com.graduation.backend.auth.api.dto.WechatLoginRequest;
import com.graduation.backend.auth.application.wechat.WechatClient;
import com.graduation.backend.auth.application.wechat.WechatSession;
import com.graduation.backend.auth.domain.RefreshToken;
import com.graduation.backend.auth.domain.RefreshTokenRepository;
import com.graduation.backend.common.domain.Campus;
import com.graduation.backend.common.domain.CampusRepository;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.common.security.AccessTokenIssuer;
import com.graduation.backend.common.security.JwtProperties;
import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.user.domain.User;
import com.graduation.backend.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 登录、刷新与登出的写事务边界。
 *
 * <p>契约要求登录与刷新都不接受校区字段：登录时按 {@code MAIN} 自动绑定，刷新时完全不动校区。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CampusRepository campusRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final RefreshTokenCodec refreshTokenCodec;
    private final AccessTokenIssuer accessTokenIssuer;
    private final WechatClient wechatClient;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthService(UserRepository userRepository,
                       CampusRepository campusRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       RefreshTokenRevoker refreshTokenRevoker,
                       RefreshTokenCodec refreshTokenCodec,
                       AccessTokenIssuer accessTokenIssuer,
                       WechatClient wechatClient,
                       JwtProperties jwtProperties,
                       Clock clock) {
        this.userRepository = userRepository;
        this.campusRepository = campusRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.refreshTokenCodec = refreshTokenCodec;
        this.accessTokenIssuer = accessTokenIssuer;
        this.wechatClient = wechatClient;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Transactional
    public TokenResponse login(WechatLoginRequest request) {
        Instant now = clock.instant();
        WechatSession session = wechatClient.exchange(request.code());
        Campus mainCampus = mainCampus();

        User user = userRepository.findByOpenid(session.openid())
                .orElseGet(() -> userRepository.save(
                        User.register(session.openid(), defaultNickname(session.openid()), mainCampus, now)));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_DISABLED, "账号已被禁用，请联系管理员");
        }
        if (user.getCampus() == null) {
            user.bindCampus(mainCampus, now);
        }
        user.recordLogin(now);
        return issue(user, request.deviceId(), now);
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokenRepository.findByTokenHash(refreshTokenCodec.hash(request.refreshToken()))
                .orElseThrow(AuthService::refreshInvalid);

        if (current.isRevoked()) {
            // 已轮换的令牌被再次使用：视为泄露，撤销该设备全部有效令牌后拒绝。
            refreshTokenRevoker.revokeAllForDevice(current.getUserId(), current.getDeviceId());
            throw refreshInvalid();
        }
        if (current.isExpired(now) || !current.getDeviceId().equals(request.deviceId())) {
            throw refreshInvalid();
        }
        User user = userRepository.findById(current.getUserId()).orElseThrow(AuthService::refreshInvalid);
        if (!user.isActive()) {
            throw refreshInvalid();
        }

        String plainNext = refreshTokenCodec.newToken();
        RefreshToken next = refreshTokenRepository.save(RefreshToken.issue(
                user.getId(), refreshTokenCodec.hash(plainNext), current.getDeviceId(),
                now.plus(jwtProperties.refreshTtl()), now));
        current.revoke(now);
        current.replacedBy(next.getId());
        return build(user, plainNext, now);
    }

    /** 幂等：令牌不存在或已撤销同样按成功处理，避免前端登出重试被 401 打断。 */
    @Transactional
    public void logout(String refreshTokenPlain) {
        Instant now = clock.instant();
        refreshTokenRepository.findByTokenHash(refreshTokenCodec.hash(refreshTokenPlain))
                .ifPresent(token -> token.revoke(now));
    }

    private TokenResponse issue(User user, String deviceId, Instant now) {
        String plain = refreshTokenCodec.newToken();
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(), refreshTokenCodec.hash(plain), deviceId, now.plus(jwtProperties.refreshTtl()), now));
        return build(user, plain, now);
    }

    private TokenResponse build(User user, String refreshTokenPlain, Instant now) {
        return new TokenResponse(
                accessTokenIssuer.issue(user.getId()),
                refreshTokenPlain,
                TokenResponse.BEARER,
                (int) accessTokenIssuer.accessTtlSeconds(),
                (int) accessTokenIssuer.refreshTtlSeconds(),
                UserSummaryResponse.from(user),
                user.getCertificationStatus());
    }

    private Campus mainCampus() {
        return campusRepository.findByCode(Campus.MAIN_CODE)
                .orElseThrow(() -> new IllegalStateException("缺少 code=MAIN 的校区种子数据，请检查 V5 迁移"));
    }

    /** 默认昵称由 openid 派生，不含任何个人信息，长度固定不超过 10 个字符。 */
    private String defaultNickname(String openid) {
        String compact = openid.replaceAll("[^A-Za-z0-9]", "");
        String suffix = compact.length() <= 8 ? compact : compact.substring(compact.length() - 8);
        return "用户" + suffix;
    }

    private static BusinessException refreshInvalid() {
        return new BusinessException(ErrorCode.AUTH_REFRESH_INVALID, "登录状态已失效，请重新登录");
    }
}
