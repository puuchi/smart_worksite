package com.xd.smartworksite.file.controller;

import com.xd.smartworksite.common.result.ApiResponse;
import com.xd.smartworksite.common.result.PageResult;
import com.xd.smartworksite.file.application.FileObjectApplicationService;
import com.xd.smartworksite.file.dto.FileAccessUrlResponse;
import com.xd.smartworksite.file.dto.FileObjectResponse;
import com.xd.smartworksite.file.dto.FileQueryRequest;
import com.xd.smartworksite.file.dto.FileUploadRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@Validated
public class FileObjectController {

    private final FileObjectApplicationService fileObjectApplicationService;

    public FileObjectController(FileObjectApplicationService fileObjectApplicationService) {
        this.fileObjectApplicationService = fileObjectApplicationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileObjectResponse> uploadFile(@Valid @ModelAttribute FileUploadRequest request) {
        return ApiResponse.success(fileObjectApplicationService.upload(request));
    }

    @GetMapping
    public ApiResponse<PageResult<FileObjectResponse>> listFiles(@Valid FileQueryRequest request) {
        return ApiResponse.success(fileObjectApplicationService.queryFiles(request));
    }

    @GetMapping("/{fileId}")
    public ApiResponse<FileObjectResponse> getFile(@PathVariable Long fileId) {
        return ApiResponse.success(fileObjectApplicationService.getFile(fileId));
    }

    @GetMapping("/{fileId}/access-url")
    public ApiResponse<FileAccessUrlResponse> createAccessUrl(@PathVariable Long fileId,
                                                              @RequestParam String usage,
                                                              @RequestParam(required = false) Integer expireSeconds) {
        return ApiResponse.success(fileObjectApplicationService.createAccessUrl(fileId, usage, expireSeconds));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable Long fileId) {
        fileObjectApplicationService.deleteFile(fileId);
        return ApiResponse.success();
    }
}
