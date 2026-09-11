package com.graduation.backend.common.security;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 从安全上下文取出当前用户 ID。
 *
 * <p>刻意不读数据库：ID 之外的授权判断必须在业务事务内重新读库，
 * 否则会拿到已脱离持久化上下文的实体，写操作会静默丢失。
 */
@Component
public class CurrentUserService {

    public Long requireCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "未认证或凭证无效");
        }
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "未认证或凭证无效");
        }
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "未认证或凭证无效");
        }
    }
}
