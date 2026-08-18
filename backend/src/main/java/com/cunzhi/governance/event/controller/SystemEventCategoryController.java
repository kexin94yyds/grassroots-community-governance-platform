package com.cunzhi.governance.event.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.event.dto.EventCategoryCreateRequest;
import com.cunzhi.governance.event.dto.EventCategoryUpdateRequest;
import com.cunzhi.governance.event.dto.EventCategoryView;
import com.cunzhi.governance.event.service.EventCategoryService;
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
@RequestMapping("/api/system/event-categories")
@PreAuthorize("@authz.hasPermission('event:category:manage')")
public class SystemEventCategoryController {

    private final EventCategoryService service;

    public SystemEventCategoryController(EventCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<EventCategoryView>> findAll() {
        return ApiResponse.ok(service.findAll());
    }

    @PostMapping
    public ApiResponse<EventCategoryView> create(@Valid @RequestBody EventCategoryCreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<EventCategoryView> update(
            @PathVariable String id,
            @Valid @RequestBody EventCategoryUpdateRequest request
    ) {
        return ApiResponse.ok(service.update(id, request));
    }
}
