package com.cunzhi.governance.announcement.controller;

import com.cunzhi.governance.announcement.dto.AnnouncementActionRequest;
import com.cunzhi.governance.announcement.dto.AnnouncementCreateRequest;
import com.cunzhi.governance.announcement.dto.AnnouncementFlowView;
import com.cunzhi.governance.announcement.dto.AnnouncementUpdateRequest;
import com.cunzhi.governance.announcement.dto.AnnouncementView;
import com.cunzhi.governance.announcement.service.AnnouncementService;
import com.cunzhi.governance.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    @PreAuthorize("@authz.hasPermission('announcement:read')")
    public ApiResponse<List<AnnouncementView>> findVisible() {
        return ApiResponse.ok(announcementService.findVisible());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('announcement:read')")
    public ApiResponse<AnnouncementView> findById(@PathVariable String id) {
        return ApiResponse.ok(announcementService.findById(id));
    }

    @GetMapping("/{id}/flows")
    @PreAuthorize("@authz.hasPermission('announcement:read')")
    public ApiResponse<List<AnnouncementFlowView>> findFlows(@PathVariable String id) {
        return ApiResponse.ok(announcementService.findFlows(id));
    }

    @PostMapping
    @PreAuthorize("@authz.hasPermission('announcement:global:write') or @authz.hasPermission('announcement:community:write')")
    public ApiResponse<AnnouncementView> create(@Valid @RequestBody AnnouncementCreateRequest request) {
        return ApiResponse.ok(announcementService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('announcement:global:write') or @authz.hasPermission('announcement:community:write')")
    public ApiResponse<AnnouncementView> update(
            @PathVariable String id,
            @Valid @RequestBody AnnouncementUpdateRequest request
    ) {
        return ApiResponse.ok(announcementService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@authz.hasPermission('announcement:global:write') or @authz.hasPermission('announcement:community:write')")
    public ApiResponse<AnnouncementView> publish(
            @PathVariable String id,
            @Valid @RequestBody AnnouncementActionRequest request
    ) {
        return ApiResponse.ok(announcementService.publish(id, request));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@authz.hasPermission('announcement:global:write') or @authz.hasPermission('announcement:community:write')")
    public ApiResponse<AnnouncementView> withdraw(
            @PathVariable String id,
            @Valid @RequestBody AnnouncementActionRequest request
    ) {
        return ApiResponse.ok(announcementService.withdraw(id, request));
    }
}
