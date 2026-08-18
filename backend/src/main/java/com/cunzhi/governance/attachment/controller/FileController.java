package com.cunzhi.governance.attachment.controller;

import com.cunzhi.governance.attachment.service.EventAttachmentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final EventAttachmentService attachmentService;

    public FileController(EventAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('file:read')")
    public ResponseEntity<InputStreamResource> download(@PathVariable String id) throws IOException {
        EventAttachmentService.AttachmentDownload download = attachmentService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.metadata().contentType()))
                .contentLength(download.metadata().fileSize())
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.metadata().originalName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new InputStreamResource(Files.newInputStream(download.path())));
    }
}
