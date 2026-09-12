package com.graduation.backend.file.application;

import com.graduation.backend.common.domain.FileBizType;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.file.api.dto.FileObjectResponse;
import com.graduation.backend.file.config.FileProperties;
import com.graduation.backend.file.domain.FileObject;
import com.graduation.backend.file.domain.FileObjectRepository;
import com.graduation.backend.user.domain.User;
import com.graduation.backend.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * 文件上传与读取。
 *
 * <p>内容先落盘、元数据后入库；两者不在同一事务中，因此在事务回滚时补偿删除已写入的对象，
 * 不留下「有文件没记录」的孤儿对象。
 */
@Service
public class FileService {

    private static final HexFormat HEX = HexFormat.of();
    private static final int MAX_ORIGINAL_NAME = 255;

    private final FileObjectRepository fileObjectRepository;
    private final FileStorage fileStorage;
    private final FileProperties fileProperties;
    private final UserRepository userRepository;
    private final Clock clock;

    public FileService(FileObjectRepository fileObjectRepository, FileStorage fileStorage,
                       FileProperties fileProperties, UserRepository userRepository, Clock clock) {
        this.fileObjectRepository = fileObjectRepository;
        this.fileStorage = fileStorage;
        this.fileProperties = fileProperties;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public FileObjectResponse upload(FileBizType bizType, MultipartFile file, Long ownerId) {
        Long uploaderId = requireActiveUploader(ownerId).getId();
        long maxBytes = FilePolicy.maxBytes(bizType);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件为空");
        }
        if (file.getSize() > maxBytes) {
            throw tooLarge(maxBytes);
        }
        byte[] content = readAll(file);
        if (content.length > maxBytes) {
            throw tooLarge(maxBytes);
        }

        byte[] head = Arrays.copyOf(content, Math.min(content.length, FilePolicy.HEAD_BYTES));
        ImageFormat format = FilePolicy.inspect(bizType, content.length, head,
                file.getContentType(), file.getOriginalFilename());

        String objectKey = fileStorage.store(bizType, format.canonicalExtension(), content);
        registerCompensatingDelete(objectKey);

        FileObject saved = fileObjectRepository.save(FileObject.uploaded(
                uploaderId,
                bizType,
                objectKey,
                originalName(file.getOriginalFilename(), format),
                format.contentType(),
                (long) content.length,
                sha256(content),
                clock.instant()));
        return FileObjectResponse.from(saved, fileProperties);
    }

    @Transactional(readOnly = true)
    public FileObject requireReadable(Long fileId) {
        return fileObjectRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
    }

    /**
     * 校验头像文件可用于绑定：必须属于当前用户、业务类型为 AVATAR、且仍未被占用。
     * 返回实体由调用方在自身事务内完成绑定。
     */
    @Transactional(readOnly = true)
    public FileObject requireBindableAvatar(Long fileId, Long ownerId) {
        FileObject file = fileObjectRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
        if (!file.isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.FILE_NOT_OWNED, "无权使用该文件");
        }
        if (file.getBizType() != FileBizType.AVATAR) {
            throw new BusinessException(ErrorCode.FILE_INVALID_TYPE, "该文件不是头像文件");
        }
        if (!file.isAvailable()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "该文件已被使用");
        }
        return file;
    }

    /**
     * 上传前重新读库校验账号状态。
     *
     * <p>这里直接依赖 {@code UserRepository} 而不是 {@code UserService}：后者需要本服务校验头像归属，
     * 反向依赖会构成构造器循环。两处状态判断都只有这一种取值口径（401 / 403）。
     */
    private User requireActiveUploader(Long ownerId) {
        User user = userRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "登录状态已失效，请重新登录"));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_DISABLED, "账号已被禁用，请联系管理员");
        }
        return user;
    }

    /** 事务回滚（含提交失败）时删除已落盘的对象，避免残留孤儿文件。 */
    private void registerCompensatingDelete(String objectKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    fileStorage.delete(objectKey);
                }
            }
        });
    }

    private byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取上传内容失败");
        }
    }

    private String originalName(String originalName, ImageFormat format) {
        if (originalName == null || originalName.isBlank()) {
            return "upload." + format.canonicalExtension();
        }
        String sanitized = originalName.replace('\\', '/');
        int slash = sanitized.lastIndexOf('/');
        if (slash >= 0) {
            sanitized = sanitized.substring(slash + 1);
        }
        if (sanitized.isBlank()) {
            return "upload." + format.canonicalExtension();
        }
        return sanitized.length() > MAX_ORIGINAL_NAME ? sanitized.substring(0, MAX_ORIGINAL_NAME) : sanitized;
    }

    private String sha256(byte[] content) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }

    private BusinessException tooLarge(long maxBytes) {
        return new BusinessException(ErrorCode.FILE_TOO_LARGE, "文件超过大小上限 " + (maxBytes / 1024 / 1024) + " MB");
    }
}
