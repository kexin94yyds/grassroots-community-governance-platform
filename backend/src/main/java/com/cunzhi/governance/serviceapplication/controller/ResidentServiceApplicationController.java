package com.cunzhi.governance.serviceapplication.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationActionRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationCreateRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationRateRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationView;
import com.cunzhi.governance.serviceapplication.service.ServiceApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resident-portal/service-applications")
@PreAuthorize("@authz.hasPermission('resident:portal')")
public class ResidentServiceApplicationController {

    private final ServiceApplicationService service;

    public ResidentServiceApplicationController(ServiceApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ServiceApplicationView>> findMine() {
        return ApiResponse.ok(service.findForCurrentResident());
    }

    @PostMapping
    @PreAuthorize("@authz.hasPermission('resident:portal') and @authz.hasPermission('service:application:apply')")
    public ApiResponse<ServiceApplicationView> apply(@Valid @RequestBody ServiceApplicationCreateRequest request) {
        return ApiResponse.ok(service.apply(request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@authz.hasPermission('resident:portal') and @authz.hasPermission('service:application:cancel')")
    public ApiResponse<ServiceApplicationView> cancel(@PathVariable String id, @Valid @RequestBody ServiceApplicationActionRequest request) {
        return ApiResponse.ok(service.cancel(id, request));
    }

    @PostMapping("/{id}/rate")
    @PreAuthorize("@authz.hasPermission('resident:portal') and @authz.hasPermission('service:application:rate')")
    public ApiResponse<ServiceApplicationView> rate(@PathVariable String id, @Valid @RequestBody ServiceApplicationRateRequest request) {
        return ApiResponse.ok(service.rate(id, request));
    }
}
