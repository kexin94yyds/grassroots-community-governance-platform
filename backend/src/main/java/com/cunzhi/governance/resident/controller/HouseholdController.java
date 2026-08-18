package com.cunzhi.governance.resident.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.resident.dto.HouseholdSummary;
import com.cunzhi.governance.resident.dto.HouseholdCreateRequest;
import com.cunzhi.governance.resident.dto.HouseholdStatusRequest;
import com.cunzhi.governance.resident.dto.HouseholdUpdateRequest;
import com.cunzhi.governance.resident.service.HouseholdService;
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

@Validated
@RestController
@RequestMapping("/api/households")
public class HouseholdController {

    private final HouseholdService householdService;

    public HouseholdController(HouseholdService householdService) {
        this.householdService = householdService;
    }

    @GetMapping
    @PreAuthorize("@authz.hasPermission('resident:read')")
    public ApiResponse<PageResponse<HouseholdSummary>> findPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String gridId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(householdService.findPage(keyword, gridId, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('resident:read')")
    public ApiResponse<HouseholdSummary> findById(@PathVariable String id) {
        return ApiResponse.ok(householdService.findById(id));
    }

    @PostMapping
    @PreAuthorize("@authz.hasPermission('resident:write')")
    public ApiResponse<HouseholdSummary> create(@Valid @RequestBody HouseholdCreateRequest request) {
        return ApiResponse.ok(householdService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('resident:write')")
    public ApiResponse<HouseholdSummary> update(
            @PathVariable String id,
            @Valid @RequestBody HouseholdUpdateRequest request
    ) {
        return ApiResponse.ok(householdService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authz.hasPermission('resident:write')")
    public ApiResponse<HouseholdSummary> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody HouseholdStatusRequest request
    ) {
        return ApiResponse.ok(householdService.updateStatus(id, request));
    }
}
