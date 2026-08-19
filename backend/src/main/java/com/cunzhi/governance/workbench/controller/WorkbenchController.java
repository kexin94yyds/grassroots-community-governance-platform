package com.cunzhi.governance.workbench.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.workbench.dto.SystemHealthView;
import com.cunzhi.governance.workbench.dto.SystemOperationView;
import com.cunzhi.governance.workbench.dto.WorkbenchSummary;
import com.cunzhi.governance.workbench.service.WorkbenchService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkbenchController {

    private final WorkbenchService service;

    public WorkbenchController(WorkbenchService service) {
        this.service = service;
    }

    @GetMapping("/api/workbenches/admin/summary")
    @PreAuthorize("@authz.hasPermission('workbench:admin:read')")
    public ApiResponse<WorkbenchSummary> adminSummary() {
        return ApiResponse.ok(service.adminSummary());
    }

    @GetMapping("/api/workbenches/community/summary")
    @PreAuthorize("@authz.hasPermission('workbench:community:read')")
    public ApiResponse<WorkbenchSummary> communitySummary() {
        return ApiResponse.ok(service.communitySummary());
    }

    @GetMapping("/api/workbenches/grid/summary")
    @PreAuthorize("@authz.hasPermission('workbench:grid:read')")
    public ApiResponse<WorkbenchSummary> gridSummary() {
        return ApiResponse.ok(service.gridSummary());
    }

    @GetMapping("/api/workbenches/resident/summary")
    @PreAuthorize("@authz.hasPermission('workbench:resident:read')")
    public ApiResponse<WorkbenchSummary> residentSummary() {
        return ApiResponse.ok(service.residentSummary());
    }

    @GetMapping("/api/system/operations")
    @PreAuthorize("@authz.hasPermission('system:audit:read')")
    public ApiResponse<PageResponse<SystemOperationView>> operations(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.operations(module, keyword, page, size));
    }

    @GetMapping("/api/system/health")
    @PreAuthorize("@authz.hasPermission('system:health:read')")
    public ApiResponse<SystemHealthView> health() {
        return ApiResponse.ok(service.health());
    }
}
