package com.cunzhi.governance.resident.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.resident.dto.ResidentSummary;
import com.cunzhi.governance.resident.dto.ResidentCreateRequest;
import com.cunzhi.governance.resident.dto.ResidentSensitiveSearchRequest;
import com.cunzhi.governance.resident.dto.ResidentSensitiveView;
import com.cunzhi.governance.resident.dto.ResidentSensitiveAccessLogView;
import com.cunzhi.governance.resident.dto.ResidentSensitiveViewRequest;
import com.cunzhi.governance.resident.dto.ResidentStatusRequest;
import com.cunzhi.governance.resident.dto.ResidentUpdateRequest;
import com.cunzhi.governance.resident.service.ResidentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/residents")
public class ResidentController {

    private final ResidentService residentService;

    public ResidentController(ResidentService residentService) {
        this.residentService = residentService;
    }

    @GetMapping
    @PreAuthorize("@authz.hasPermission('resident:read')")
    public ApiResponse<PageResponse<ResidentSummary>> findPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String gridId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(residentService.findPage(keyword, gridId, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('resident:read')")
    public ApiResponse<ResidentSummary> findById(@PathVariable String id) {
        return ApiResponse.ok(residentService.findById(id));
    }

    @PostMapping("/sensitive-search")
    @PreAuthorize("@authz.hasPermission('resident:sensitive:read')")
    public ResponseEntity<ApiResponse<PageResponse<ResidentSummary>>> findBySensitiveValue(
            @Valid @RequestBody ResidentSensitiveSearchRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(residentService.findBySensitiveValue(request)));
    }

    @PostMapping("/{id}/sensitive-view")
    @PreAuthorize("@authz.hasPermission('resident:sensitive:read')")
    public ResponseEntity<ApiResponse<ResidentSensitiveView>> viewSensitive(
            @PathVariable String id,
            @Valid @RequestBody ResidentSensitiveViewRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(residentService.viewSensitive(id, request)));
    }

    @GetMapping("/sensitive-access-logs")
    @PreAuthorize("@authz.hasPermission('resident:sensitive:audit:read')")
    public ResponseEntity<ApiResponse<PageResponse<ResidentSensitiveAccessLogView>>> findSensitiveAccessLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String fieldType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(residentService.findSensitiveAccessLogs(action, fieldType, keyword, page, size)));
    }

    @PostMapping
    @PreAuthorize("@authz.hasPermission('resident:write')")
    public ApiResponse<ResidentSummary> create(@Valid @RequestBody ResidentCreateRequest request) {
        return ApiResponse.ok(residentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('resident:write')")
    public ApiResponse<ResidentSummary> update(
            @PathVariable String id,
            @Valid @RequestBody ResidentUpdateRequest request
    ) {
        return ApiResponse.ok(residentService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authz.hasPermission('resident:write')")
    public ApiResponse<ResidentSummary> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody ResidentStatusRequest request
    ) {
        return ApiResponse.ok(residentService.updateStatus(id, request));
    }
}
