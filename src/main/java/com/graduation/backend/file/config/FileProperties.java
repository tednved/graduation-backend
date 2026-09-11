package com.graduation.backend.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件存储配置。
 *
 * <p>{@code storageRoot} 是对象文件的落盘根目录，默认 {@code ./var/media}（不纳入版本控制）；
 * {@code publicBaseUrl} 是返回给客户端的受控访问前缀，与内部 objectKey 无关。
 */
@ConfigurationProperties(prefix = "app.file")
public record FileProperties(String storageRoot, String publicBaseUrl) {

    public static final String DEFAULT_STORAGE_ROOT = "./var/media";
    public static final String DEFAULT_PUBLIC_BASE_URL = "/media";

    public FileProperties {
        if (storageRoot == null || storageRoot.isBlank()) {
            storageRoot = DEFAULT_STORAGE_ROOT;
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = DEFAULT_PUBLIC_BASE_URL;
        }
        while (publicBaseUrl.endsWith("/")) {
            publicBaseUrl = publicBaseUrl.substring(0, publicBaseUrl.length() - 1);
        }
    }

    public Path storagePath() {
        return Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    public String publicUrlFor(Long fileId) {
        return publicBaseUrl + "/" + fileId;
    }
}
