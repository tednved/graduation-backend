package com.graduation.backend.file.api.dto;

import com.graduation.backend.common.domain.FileBizType;
import com.graduation.backend.file.config.FileProperties;
import com.graduation.backend.file.domain.FileObject;

import java.time.Instant;

/**
 * 上传结果。
 *
 * <p>{@code url} 是受控访问地址（由 fileId 反查内部对象），不包含对象存储的 objectKey 或路径。
 * {@code sizeBytes} 在契约里是 JSON number，故用 {@code Integer} 声明。
 */
public record FileObjectResponse(
        Long fileId,
        String url,
        FileBizType bizType,
        String originalName,
        String contentType,
        Integer sizeBytes,
        String sha256,
        Instant createdAt) {

    public static FileObjectResponse from(FileObject file, FileProperties properties) {
        return new FileObjectResponse(
                file.getId(),
                properties.publicUrlFor(file.getId()),
                file.getBizType(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes().intValue(),
                file.getSha256(),
                file.getCreatedAt());
    }
}
