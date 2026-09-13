package com.graduation.backend.common.web;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;

/**
 * 把用户输入的关键词转成 LIKE 模式。
 *
 * <p>{@code %} 与 {@code _} 是用户输入而不是通配符，统一用 {@code !} 转义，
 * 与 XML 里的 {@code ESCAPE '!'} 对应；纯空白视为不筛选（返回 {@code null}），
 * 超长按参数错误处理，避免把任意长的字符串送进数据库。
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    public static String contains(String keyword, int maxLength) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "关键词长度需在 1~" + maxLength + " 之间");
        }
        String escaped = trimmed.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
