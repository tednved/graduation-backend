package com.graduation.backend.file.application;

import com.graduation.backend.common.domain.FileBizType;
import org.springframework.core.io.Resource;

/**
 * 对象内容存储。
 *
 * <p>元数据在 MySQL，内容在存储介质，两者不在同一个事务里，因此写入失败必须补偿删除。
 */
public interface FileStorage {

    /** 写入内容并返回内部 objectKey。objectKey 由实现生成，调用方不得自行拼接。 */
    String store(FileBizType bizType, String extension, byte[] content);

    /** 补偿删除：元数据落库失败时调用，尽力清理已写入的内容。 */
    void delete(String objectKey);

    Resource load(String objectKey);
}
