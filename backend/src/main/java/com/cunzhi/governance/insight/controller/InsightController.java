package com.cunzhi.governance.insight.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.insight.dto.ModuleInsights;
import com.cunzhi.governance.insight.service.InsightService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping("/users")
    @PreAuthorize("@authz.hasPermission('system:user:manage')")
    public ApiResponse<ModuleInsights.UserInsight> users() {
        return ApiResponse.ok(insightService.users());
    }

    @GetMapping("/grids")
    @PreAuthorize("@authz.hasPermission('grid:read')")
    public ApiResponse<ModuleInsights.GridInsight> grids() {
        return ApiResponse.ok(insightService.grids());
    }

    @GetMapping("/residents")
    @PreAuthorize("@authz.hasPermission('resident:read')")
    public ApiResponse<ModuleInsights.ResidentInsight> residents() {
        return ApiResponse.ok(insightService.residents());
    }

    @GetMapping("/events")
    @PreAuthorize("@authz.hasPermission('event:read')")
    public ApiResponse<ModuleInsights.EventInsight> events() {
        return ApiResponse.ok(insightService.events());
    }

    @GetMapping("/tasks")
    @PreAuthorize("@authz.hasPermission('task:read')")
    public ApiResponse<ModuleInsights.TaskInsight> tasks() {
        return ApiResponse.ok(insightService.tasks());
    }
}
