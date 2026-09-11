package com.graduation.backend.file.api;

import com.graduation.backend.file.application.FileService;
import com.graduation.backend.file.application.FileStorage;
import com.graduation.backend.file.domain.FileObject;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 图片受控读取入口。
 *
 * <p>刻意放在 {@code /api/v1} 之外：契约把这里的地址当作「实现细节的受控 URL」，
 * 只暴露 fileId，不暴露对象存储内部 key。图片与头像公开可读，当前 MVP 不存在可读的证据文件。
 */
@RestController
public class MediaController {

    private final FileService fileService;
    private final FileStorage fileStorage;

    public MediaController(FileService fileService, FileStorage fileStorage) {
        this.fileService = fileService;
        this.fileStorage = fileStorage;
    }

    @GetMapping("/media/{fileId}")
    public ResponseEntity<Resource> read(@PathVariable Long fileId) {
        FileObject file = fileService.requireReadable(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(fileStorage.load(file.getObjectKey()));
    }
}
