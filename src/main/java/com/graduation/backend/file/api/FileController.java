package com.graduation.backend.file.api;

import com.graduation.backend.common.domain.FileBizType;
import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.file.api.dto.FileObjectResponse;
import com.graduation.backend.file.application.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 文件上传。字段名固定为 {@code file}，大小与类型规则见 {@code FilePolicy}。 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;
    private final CurrentUserService currentUserService;

    public FileController(FileService fileService, CurrentUserService currentUserService) {
        this.fileService = fileService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FileObjectResponse> upload(@RequestParam("bizType") FileBizType bizType,
                                                  @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(fileService.upload(bizType, file, currentUserService.requireCurrentUserId()));
    }
}
