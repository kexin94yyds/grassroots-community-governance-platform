package com.cunzhi.governance.dashboard.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.dashboard.dto.DashboardOverview;
import com.cunzhi.governance.dashboard.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    @PreAuthorize("@authz.hasPermission('dashboard:read')")
    public ApiResponse<DashboardOverview> overview() {
        return ApiResponse.ok(dashboardService.overview());
    }
}
