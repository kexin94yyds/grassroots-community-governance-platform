package com.cunzhi.governance.task.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.task.dto.TaskAttachmentView;
import com.cunzhi.governance.task.service.TaskAttachmentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}/attachments")
public class TaskAttachmentController {

    private final TaskAttachmentService service;

    public TaskAttachmentController(TaskAttachmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@authz.hasPermission('task:read')")
    public ApiResponse<List<TaskAttachmentView>> findByTaskId(@PathVariable String taskId) {
        return ApiResponse.ok(service.findByTaskId(taskId));
    }

    @PostMapping
    @PreAuthorize("@authz.hasPermission('task:handle')")
    public ApiResponse<TaskAttachmentView> upload(
            @PathVariable String taskId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "requestToken", required = false) String requestToken
    ) {
        return ApiResponse.ok(service.upload(taskId, file, requestToken));
    }

    @GetMapping("/{attachmentId}/content")
    @PreAuthorize("@authz.hasPermission('task:read')")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String taskId,
            @PathVariable String attachmentId
    ) throws IOException {
        TaskAttachmentService.AttachmentDownload download = service.download(taskId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.metadata().contentType()))
                .contentLength(download.metadata().fileSize())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.metadata().originalName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(Files.newInputStream(download.path())));
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("@authz.hasPermission('file:delete')")
    public ApiResponse<Void> delete(
            @PathVariable String taskId,
            @PathVariable String attachmentId
    ) {
        service.delete(taskId, attachmentId);
        return ApiResponse.ok();
    }
}
