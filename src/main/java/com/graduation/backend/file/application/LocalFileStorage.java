package com.graduation.backend.file.application;

import com.graduation.backend.common.domain.FileBizType;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.file.config.FileProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * 本地磁盘实现。
 *
 * <p>objectKey 完全由服务端生成（业务类型/年月/随机十六进制），因此不含任何用户输入，
 * 从根上排除了路径穿越；{@link #resolve} 仍额外校验归一化后仍位于根目录内，作为纵深防御。
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final HexFormat HEX = HexFormat.of();

    private final Path root;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public LocalFileStorage(FileProperties properties, Clock clock) {
        this.root = properties.storagePath();
        this.clock = clock;
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建文件存储根目录: " + root, ex);
        }
    }

    @Override
    public String store(FileBizType bizType, String extension, byte[] content) {
        String objectKey = newObjectKey(bizType, extension);
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件写入失败，请稍后重试");
        }
        return objectKey;
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException | RuntimeException ex) {
            log.warn("补偿删除对象失败，需人工清理残留文件", ex);
        }
    }

    @Override
    public Resource load(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.isReadable(target)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
        return new FileSystemResource(target);
    }

    private String newObjectKey(FileBizType bizType, String extension) {
        byte[] entropy = new byte[16];
        random.nextBytes(entropy);
        String datePart = LocalDate.now(clock).format(MONTH);
        return bizType.name().toLowerCase() + "/" + datePart + "/" + HEX.formatHex(entropy) + "." + extension;
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
        return resolved;
    }
}
