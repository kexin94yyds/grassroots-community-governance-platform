package com.cunzhi.governance.system.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.system.dto.MenuItem;
import com.cunzhi.governance.system.dto.MenuUpdateRequest;
import com.cunzhi.governance.system.dto.RoleOption;
import com.cunzhi.governance.system.dto.RoleUpdateRequest;
import com.cunzhi.governance.system.service.SystemAccessService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system")
public class SystemCatalogController {

    private final SystemAccessService systemAccessService;

    public SystemCatalogController(SystemAccessService systemAccessService) {
        this.systemAccessService = systemAccessService;
    }

    @GetMapping("/roles")
    @PreAuthorize("@authz.hasPermission('system:role:manage')")
    public ApiResponse<List<RoleOption>> roles() {
        return ApiResponse.ok(systemAccessService.findRoles());
    }

    @PutMapping("/roles/{code}")
    @PreAuthorize("@authz.hasPermission('system:role:manage')")
    public ApiResponse<RoleOption> updateRole(
            @PathVariable String code,
            @Valid @RequestBody RoleUpdateRequest request
    ) {
        return ApiResponse.ok(systemAccessService.updateRole(code, request));
    }

    @GetMapping("/menus")
    @PreAuthorize("@authz.hasPermission('system:menu:manage')")
    public ApiResponse<List<MenuItem>> menus() {
        return ApiResponse.ok(systemAccessService.findMenus());
    }

    @PutMapping("/menus/{id}")
    @PreAuthorize("@authz.hasPermission('system:menu:manage')")
    public ApiResponse<MenuItem> updateMenu(
            @PathVariable String id,
            @Valid @RequestBody MenuUpdateRequest request
    ) {
        return ApiResponse.ok(systemAccessService.updateMenu(id, request));
    }
}
