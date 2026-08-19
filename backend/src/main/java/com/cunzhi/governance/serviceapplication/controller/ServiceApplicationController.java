package com.cunzhi.governance.serviceapplication.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationActionRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationFlowView;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationView;
import com.cunzhi.governance.serviceapplication.service.ServiceApplicationService;
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
@RequestMapping("/api/service-applications")
public class ServiceApplicationController {

    private final ServiceApplicationService service;

    public ServiceApplicationController(ServiceApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@authz.hasPermission('service:application:read')")
    public ApiResponse<PageResponse<ServiceApplicationView>> findPage(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(service.findScoped(status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('service:application:read')")
    public ApiResponse<ServiceApplicationView> findById(@PathVariable String id) {
        return ApiResponse.ok(service.findScopedById(id));
    }

    @GetMapping("/{id}/flows")
    @PreAuthorize("@authz.hasPermission('service:application:read')")
    public ApiResponse<List<ServiceApplicationFlowView>> findFlows(@PathVariable String id) {
        return ApiResponse.ok(service.findScopedFlows(id));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("@authz.hasPermission('service:application:handle')")
    public ApiResponse<ServiceApplicationView> accept(@PathVariable String id, @Valid @RequestBody ServiceApplicationActionRequest request) {
        return ApiResponse.ok(service.accept(id, request));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("@authz.hasPermission('service:application:handle')")
    public ApiResponse<ServiceApplicationView> start(@PathVariable String id, @Valid @RequestBody ServiceApplicationActionRequest request) {
        return ApiResponse.ok(service.start(id, request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@authz.hasPermission('service:application:handle')")
    public ApiResponse<ServiceApplicationView> complete(@PathVariable String id, @Valid @RequestBody ServiceApplicationActionRequest request) {
        return ApiResponse.ok(service.complete(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@authz.hasPermission('service:application:handle')")
    public ApiResponse<ServiceApplicationView> reject(@PathVariable String id, @Valid @RequestBody ServiceApplicationActionRequest request) {
        return ApiResponse.ok(service.reject(id, request));
    }
}
