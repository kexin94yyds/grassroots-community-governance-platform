package com.cunzhi.governance.attachment.controller;

import com.cunzhi.governance.attachment.dto.EventAttachmentView;
import com.cunzhi.governance.attachment.service.EventAttachmentService;
import com.cunzhi.governance.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/attachments")
public class EventAttachmentController {

    private final EventAttachmentService attachmentService;

    public EventAttachmentController(EventAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    @PreAuthorize("@authz.hasPermission('file:read')")
    public ApiResponse<List<EventAttachmentView>> findByEventId(@PathVariable String eventId) {
        return ApiResponse.ok(attachmentService.findByEventId(eventId));
    }

    @PostMapping
    @PreAuthorize("@authz.hasPermission('file:upload')")
    public ApiResponse<EventAttachmentView> upload(
            @PathVariable String eventId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "requestToken", required = false) String requestToken
    ) {
        return ApiResponse.ok(attachmentService.upload(eventId, file, requestToken));
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("@authz.hasPermission('file:delete')")
    public ApiResponse<Void> delete(
            @PathVariable String eventId,
            @PathVariable String attachmentId
    ) {
        attachmentService.delete(eventId, attachmentId);
        return ApiResponse.ok();
    }
}
