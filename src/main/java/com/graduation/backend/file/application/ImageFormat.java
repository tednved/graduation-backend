package com.graduation.backend.file.application;

import java.util.Locale;
import java.util.Set;

/**
 * 允许上传的图片格式。
 *
 * <p>格式以文件头为准而不是客户端声明的 MIME：声明的 {@code Content-Type} 可以随意伪造，
 * 文件头不能。
 */
public enum ImageFormat {

    JPEG("image/jpeg", Set.of("jpg", "jpeg")) {
        @Override
        boolean matches(byte[] head) {
            return head.length >= 3
                    && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
        }
    },

    PNG("image/png", Set.of("png")) {
        @Override
        boolean matches(byte[] head) {
            int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            if (head.length < signature.length) {
                return false;
            }
            for (int i = 0; i < signature.length; i++) {
                if ((head[i] & 0xFF) != signature[i]) {
                    return false;
                }
            }
            return true;
        }
    },

    WEBP("image/webp", Set.of("webp")) {
        @Override
        boolean matches(byte[] head) {
            return head.length >= 12
                    && isAscii(head, 0, "RIFF")
                    && isAscii(head, 8, "WEBP");
        }
    };

    private final String contentType;
    private final Set<String> extensions;

    ImageFormat(String contentType, Set<String> extensions) {
        this.contentType = contentType;
        this.extensions = extensions;
    }

    abstract boolean matches(byte[] head);

    public String contentType() {
        return contentType;
    }

    public String canonicalExtension() {
        return extensions.iterator().next();
    }

    public boolean supportsExtension(String extension) {
        return extension != null && extensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    private static boolean isAscii(byte[] head, int offset, String expected) {
        for (int i = 0; i < expected.length(); i++) {
            if ((head[offset + i] & 0xFF) != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
