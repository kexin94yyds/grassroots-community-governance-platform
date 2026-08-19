package com.cunzhi.governance.patrol.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.patrol.dto.PatrolPlanCancelRequest;
import com.cunzhi.governance.patrol.dto.PatrolPlanCreateRequest;
import com.cunzhi.governance.patrol.dto.PatrolPlanView;
import com.cunzhi.governance.patrol.service.PatrolPlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/patrol-plans")
public class PatrolPlanController {

    private final PatrolPlanService service;

    public PatrolPlanController(PatrolPlanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@authz.hasPermission('patrol:read')")
    public ApiResponse<PageResponse<PatrolPlanView>> findPage(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(service.findScoped(page, size));
    }

    @GetMapping("/mine")
    @PreAuthorize("@authz.hasPermission('patrol:read')")
    public ApiResponse<List<PatrolPlanView>> findMine() {
        return ApiResponse.ok(service.findMine());
    }

    @PostMapping
    @PreAuthorize("@authz.hasPermission('patrol:plan:write')")
    public ApiResponse<PatrolPlanView> create(@Valid @RequestBody PatrolPlanCreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@authz.hasPermission('patrol:plan:write')")
    public ApiResponse<PatrolPlanView> cancel(
            @PathVariable String id,
            @Valid @RequestBody PatrolPlanCancelRequest request
    ) {
        return ApiResponse.ok(service.cancel(id, request));
    }
}
