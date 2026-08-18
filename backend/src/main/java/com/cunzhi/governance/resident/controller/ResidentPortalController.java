package com.cunzhi.governance.resident.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.attachment.dto.EventAttachmentView;
import com.cunzhi.governance.attachment.service.EventAttachmentService;
import com.cunzhi.governance.event.dto.EventSummary;
import com.cunzhi.governance.resident.dto.ResidentEventRequest;
import com.cunzhi.governance.resident.dto.ResidentPortalOverview;
import com.cunzhi.governance.resident.service.ResidentPortalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/resident-portal")
@PreAuthorize("@authz.hasPermission('resident:portal')")
public class ResidentPortalController {

    private final ResidentPortalService residentPortalService;
    private final EventAttachmentService eventAttachmentService;

    public ResidentPortalController(
            ResidentPortalService residentPortalService,
            EventAttachmentService eventAttachmentService
    ) {
        this.residentPortalService = residentPortalService;
        this.eventAttachmentService = eventAttachmentService;
    }

    @GetMapping("/overview")
    public ApiResponse<ResidentPortalOverview> overview() {
        return ApiResponse.ok(residentPortalService.overview());
    }

    @PostMapping("/events")
    public ApiResponse<EventSummary> report(@Valid @RequestBody ResidentEventRequest request) {
        return ApiResponse.ok(residentPortalService.report(request));
    }

    @GetMapping("/events/{eventId}/attachments")
    public ApiResponse<List<EventAttachmentView>> findEventAttachments(@PathVariable String eventId) {
        return ApiResponse.ok(eventAttachmentService.findByResidentEvent(eventId));
    }

    @PostMapping("/events/{eventId}/attachments")
    public ApiResponse<EventAttachmentView> uploadEventAttachment(
            @PathVariable String eventId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "requestToken", required = false) String requestToken
    ) {
        return ApiResponse.ok(eventAttachmentService.uploadForResident(eventId, file, requestToken));
    }

    @GetMapping("/events/{eventId}/attachments/{attachmentId}/content")
    public ResponseEntity<InputStreamResource> downloadEventAttachment(
            @PathVariable String eventId,
            @PathVariable String attachmentId
    ) throws IOException {
        EventAttachmentService.AttachmentDownload download = eventAttachmentService.downloadForResident(eventId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.metadata().contentType()))
                .contentLength(download.metadata().fileSize())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.metadata().originalName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(Files.newInputStream(download.path())));
    }

    @DeleteMapping("/events/{eventId}/attachments/{attachmentId}")
    public ApiResponse<Void> deleteEventAttachment(
            @PathVariable String eventId,
            @PathVariable String attachmentId
    ) {
        eventAttachmentService.deleteForResident(eventId, attachmentId);
        return ApiResponse.ok();
    }
}
