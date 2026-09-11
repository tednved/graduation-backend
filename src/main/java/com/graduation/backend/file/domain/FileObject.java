package com.graduation.backend.file.domain;

import com.graduation.backend.common.domain.FileBizType;
import com.graduation.backend.common.domain.FileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 对象存储文件元数据。
 *
 * <p>{@code objectKey} 是服务端生成的内部路径，任何响应都不得返回；对外只暴露主键与受控访问地址。
 */
@Entity
@Table(name = "file_objects")
public class FileObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "biz_type", nullable = false, length = 32)
    private FileBizType bizType;

    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FileStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "bound_at")
    private Instant boundAt;

    protected FileObject() {
    }

    private FileObject(Long ownerId, FileBizType bizType, String objectKey, String originalName,
                       String contentType, Long sizeBytes, String sha256, Instant createdAt) {
        this.ownerId = ownerId;
        this.bizType = bizType;
        this.objectKey = objectKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.status = FileStatus.UPLOADED;
        this.createdAt = createdAt;
    }

    public static FileObject uploaded(Long ownerId, FileBizType bizType, String objectKey, String originalName,
                                     String contentType, Long sizeBytes, String sha256, Instant createdAt) {
        return new FileObject(ownerId, bizType, objectKey, originalName, contentType, sizeBytes, sha256, createdAt);
    }

    /** 文件被业务对象引用后置为 BOUND，同一文件不允许再次被别的业务对象引用。 */
    public void bind(Instant now) {
        if (this.status != FileStatus.UPLOADED) {
            throw new IllegalStateException("文件当前状态不允许绑定: " + this.status);
        }
        this.status = FileStatus.BOUND;
        this.boundAt = now;
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public boolean isAvailable() {
        return status == FileStatus.UPLOADED;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public FileBizType getBizType() {
        return bizType;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public FileStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getBoundAt() {
        return boundAt;
    }
}
