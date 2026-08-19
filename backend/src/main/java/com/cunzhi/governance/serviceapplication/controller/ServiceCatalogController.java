package com.cunzhi.governance.serviceapplication.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.serviceapplication.dto.ServiceCatalogCreateRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceCatalogUpdateRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceCatalogView;
import com.cunzhi.governance.serviceapplication.service.ServiceCatalogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ServiceCatalogController {

    private final ServiceCatalogService service;

    public ServiceCatalogController(ServiceCatalogService service) {
        this.service = service;
    }

    @GetMapping("/api/service-catalogs")
    @PreAuthorize("@authz.hasPermission('service:catalog:read') or @authz.hasPermission('service:catalog:manage')")
    public ApiResponse<List<ServiceCatalogView>> findEnabled() {
        return ApiResponse.ok(service.findEnabled());
    }

    @GetMapping("/api/system/service-catalogs")
    @PreAuthorize("@authz.hasPermission('service:catalog:manage')")
    public ApiResponse<List<ServiceCatalogView>> findAll() {
        return ApiResponse.ok(service.findAll());
    }

    @PostMapping("/api/system/service-catalogs")
    @PreAuthorize("@authz.hasPermission('service:catalog:manage')")
    public ApiResponse<ServiceCatalogView> create(@Valid @RequestBody ServiceCatalogCreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/api/system/service-catalogs/{id}")
    @PreAuthorize("@authz.hasPermission('service:catalog:manage')")
    public ApiResponse<ServiceCatalogView> update(
            @PathVariable String id,
            @Valid @RequestBody ServiceCatalogUpdateRequest request
    ) {
        return ApiResponse.ok(service.update(id, request));
    }
}
