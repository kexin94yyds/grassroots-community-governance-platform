package com.cunzhi.governance.grid.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.grid.dto.GridSummary;
import com.cunzhi.governance.grid.dto.AreaOption;
import com.cunzhi.governance.grid.dto.GridAssignmentsRequest;
import com.cunzhi.governance.grid.dto.GridCreateRequest;
import com.cunzhi.governance.grid.dto.GridDetail;
import com.cunzhi.governance.grid.dto.GridStatusRequest;
import com.cunzhi.governance.grid.dto.GridUpdateRequest;
import com.cunzhi.governance.grid.dto.WorkerOption;
import com.cunzhi.governance.grid.service.GridService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/grids")
public class GridController {

    private final GridService gridService;

    public GridController(GridService gridService) {
        this.gridService = gridService;
    }

    @GetMapping
    @PreAuthorize("@authz.hasPermission('grid:read')")
    public ApiResponse<PageResponse<GridSummary>> findPage(
            @RequestParam(defaultValue = "GRID") String areaType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(gridService.findPage(areaType, keyword, status, page, size));
    }

    @GetMapping("/communities")
    @PreAuthorize("@authz.hasPermission('grid:read')")
    public ApiResponse<List<AreaOption>> communities() {
        return ApiResponse.ok(gridService.findCommunityOptions());
    }

    @GetMapping("/worker-options")
    @PreAuthorize("@authz.hasPermission('grid:assign')")
    public ApiResponse<List<WorkerOption>> workerOptions() {
        return ApiResponse.ok(gridService.findWorkerOptions());
    }

    @GetMapping("/community-staff-options")
    @PreAuthorize("@authz.hasPermission('grid:assign')")
    public ApiResponse<List<WorkerOption>> communityStaffOptions() {
        return ApiResponse.ok(gridService.findCommunityStaffOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('grid:read')")
    public ApiResponse<GridDetail> findById(@PathVariable String id) {
        return ApiResponse.ok(gridService.findById(id));
    }

    @PostMapping
    @PreAuthorize("@authz.hasPermission('grid:write')")
    public ApiResponse<GridDetail> create(@Valid @RequestBody GridCreateRequest request) {
        return ApiResponse.ok(gridService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('grid:write')")
    public ApiResponse<GridDetail> update(
            @PathVariable String id,
            @Valid @RequestBody GridUpdateRequest request
    ) {
        return ApiResponse.ok(gridService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authz.hasPermission('grid:write')")
    public ApiResponse<GridDetail> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody GridStatusRequest request
    ) {
        return ApiResponse.ok(gridService.updateStatus(id, request));
    }

    @PutMapping("/{id}/assignments")
    @PreAuthorize("@authz.hasPermission('grid:assign')")
    public ApiResponse<GridDetail> replaceAssignments(
            @PathVariable String id,
            @Valid @RequestBody GridAssignmentsRequest request
    ) {
        return ApiResponse.ok(gridService.replaceAssignments(id, request));
    }
}
