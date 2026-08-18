package com.cunzhi.governance.event.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.event.dto.EventActionRequest;
import com.cunzhi.governance.event.dto.EventCategoryOption;
import com.cunzhi.governance.event.dto.EventCreateRequest;
import com.cunzhi.governance.event.dto.EventDispatchRequest;
import com.cunzhi.governance.event.dto.EventFlowView;
import com.cunzhi.governance.event.dto.EventSummary;
import com.cunzhi.governance.event.service.EventService;
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
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @PreAuthorize("@authz.hasPermission('event:read')")
    public ApiResponse<PageResponse<EventSummary>> findPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(eventService.findPage(keyword, status, page, size));
    }

    @GetMapping("/categories")
    @PreAuthorize("@authz.hasPermission('event:report') or @authz.hasPermission('resident:portal')")
    public ApiResponse<List<EventCategoryOption>> findEnabledCategories() {
        return ApiResponse.ok(eventService.findEnabledCategories());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('event:read')")
    public ApiResponse<EventSummary> findById(@PathVariable String id) {
        return ApiResponse.ok(eventService.findById(id));
    }

    @GetMapping("/{id}/flows")
    @PreAuthorize("@authz.hasPermission('event:read')")
    public ApiResponse<List<EventFlowView>> findFlows(@PathVariable String id) {
        return ApiResponse.ok(eventService.findFlows(id));
    }

    @PostMapping
    @PreAuthorize("@authz.hasPermission('event:report')")
    public ApiResponse<EventSummary> report(@Valid @RequestBody EventCreateRequest request) {
        return ApiResponse.ok(eventService.report(request));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("@authz.hasPermission('event:accept')")
    public ApiResponse<EventSummary> accept(
            @PathVariable String id,
            @Valid @RequestBody EventActionRequest request
    ) {
        return ApiResponse.ok(eventService.accept(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@authz.hasPermission('event:reject')")
    public ApiResponse<EventSummary> reject(
            @PathVariable String id,
            @Valid @RequestBody EventActionRequest request
    ) {
        return ApiResponse.ok(eventService.reject(id, request));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("@authz.hasPermission('event:assign')")
    public ApiResponse<EventSummary> assign(
            @PathVariable String id,
            @Valid @RequestBody EventDispatchRequest request
    ) {
        return ApiResponse.ok(eventService.assign(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@authz.hasPermission('event:cancel')")
    public ApiResponse<EventSummary> cancel(
            @PathVariable String id,
            @Valid @RequestBody EventActionRequest request
    ) {
        return ApiResponse.ok(eventService.cancel(id, request));
    }
}
