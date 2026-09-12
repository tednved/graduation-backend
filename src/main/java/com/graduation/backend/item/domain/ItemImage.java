package com.graduation.backend.item.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 商品图片：只保存顺序与文件关联，文件本体是 {@code file_objects} 里的元数据。
 *
 * <p>{@code sortNo} 由服务端按请求里的入参顺序生成 1～9（数据库 CHECK 是 1..9，而契约允许 0 起），
 * 客户端不能指定排序值。
 */
@Entity
@Table(name = "item_images")
public class ItemImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "file_object_id", nullable = false)
    private Long fileObjectId;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    // 库列是 TINYINT UNSIGNED（1..9），而领域里用 Integer 更自然；
    // 不显式声明 JDBC 类型时 Hibernate 会期望 INTEGER，ddl-auto=validate 直接启动失败。
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ItemImage() {
    }

    private ItemImage(Long itemId, Long fileObjectId, String imageUrl, Integer sortNo, Instant now) {
        this.itemId = itemId;
        this.fileObjectId = fileObjectId;
        this.imageUrl = imageUrl;
        this.sortNo = sortNo;
        this.createdAt = now;
    }

    public static ItemImage of(Long itemId, Long fileObjectId, String imageUrl, Integer sortNo, Instant now) {
        return new ItemImage(itemId, fileObjectId, imageUrl, sortNo, now);
    }

    public Long getId() {
        return id;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getFileObjectId() {
        return fileObjectId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
