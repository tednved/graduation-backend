package com.graduation.backend.file.application;

import com.graduation.backend.common.domain.FileBizType;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;

import java.util.Locale;

/**
 * 上传文件的准入规则。集中在一处，避免不同入口对同一约束给出不同结论。
 *
 * <p>固定顺序：大小 → 文件头 → 声明的 MIME → 扩展名。大小先判，因为超限请求不应再被解析内容。
 */
public final class FilePolicy {

    public static final long ITEM_IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    public static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024;

    /** 读取多少字节就足以判定全部允许的图片格式。 */
    public static final int HEAD_BYTES = 12;

    private FilePolicy() {
    }

    public static long maxBytes(FileBizType bizType) {
        return switch (bizType) {
            case ITEM_IMAGE -> ITEM_IMAGE_MAX_BYTES;
            case AVATAR -> AVATAR_MAX_BYTES;
            case CERTIFICATION_EVIDENCE -> throw new BusinessException(
                    ErrorCode.FILE_INVALID_TYPE, "当前版本不开放认证证据上传");
        };
    }

    public static ImageFormat inspect(FileBizType bizType, long declaredSize, byte[] head,
                                      String declaredContentType, String originalName) {
        long max = maxBytes(bizType);
        if (declaredSize <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件为空");
        }
        if (declaredSize > max) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "文件超过大小上限 " + (max / 1024 / 1024) + " MB");
        }

        ImageFormat detected = detect(head);
        if (detected == null) {
            throw new BusinessException(ErrorCode.FILE_INVALID_TYPE, "只支持 JPEG、PNG、WebP 图片");
        }
        if (declaredContentType == null || !detected.contentType().equalsIgnoreCase(declaredContentType.trim())) {
            throw new BusinessException(ErrorCode.FILE_INVALID_TYPE, "文件类型与内容不一致");
        }
        String extension = extensionOf(originalName);
        if (extension == null || !detected.supportsExtension(extension)) {
            throw new BusinessException(ErrorCode.FILE_INVALID_TYPE, "文件扩展名与内容不一致");
        }
        return detected;
    }

    private static ImageFormat detect(byte[] head) {
        for (ImageFormat format : ImageFormat.values()) {
            if (format.matches(head)) {
                return format;
            }
        }
        return null;
    }

    private static String extensionOf(String originalName) {
        if (originalName == null) {
            return null;
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return null;
        }
        return originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
