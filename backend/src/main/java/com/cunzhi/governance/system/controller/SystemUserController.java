package com.cunzhi.governance.system.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.system.dto.UserSummary;
import com.cunzhi.governance.system.dto.RegistrationReviewRequest;
import com.cunzhi.governance.system.dto.UserCreateRequest;
import com.cunzhi.governance.system.dto.UserDetail;
import com.cunzhi.governance.system.dto.UserRolesRequest;
import com.cunzhi.governance.system.dto.UserStatusRequest;
import com.cunzhi.governance.system.dto.UserPasswordResetRequest;
import com.cunzhi.governance.system.dto.UserUpdateRequest;
import com.cunzhi.governance.system.service.SystemUserService;
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
@RequestMapping("/api/system/users")
public class SystemUserController {

    private final SystemUserService systemUserService;

    public SystemUserController(SystemUserService systemUserService) {
        this.systemUserService = systemUserService;
    }

    @GetMapping
    @PreAuthorize("@authz.hasPermission('system:user:manage')")
    public ApiResponse<PageResponse<UserSummary>> findPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(systemUserService.findPage(keyword, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('system:user:manage')")
    public ApiResponse<UserDetail> findById(@PathVariable String id) {
        return ApiResponse.ok(systemUserService.findById(id));
    }

    @PostMapping
    @PreAuthorize("@authz.hasPermission('system:user:manage')")
    public ApiResponse<UserDetail> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(systemUserService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('system:user:manage')")
    public ApiResponse<UserDetail> update(
            @PathVariable String id,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return ApiResponse.ok(systemUserService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authz.hasPermission('system:user:manage')")
    public ApiResponse<UserDetail> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UserStatusRequest request
    ) {
        return ApiResponse.ok(systemUserService.updateStatus(id, request));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("@authz.hasPermission('system:user:manage')")
    public ApiResponse<UserDetail> replaceRoles(
            @PathVariable String id,
            @Valid @RequestBody UserRolesRequest request
    ) {
        return ApiResponse.ok(systemUserService.replaceRoles(id, request));
    }

    @PostMapping("/{id}/password-reset")
    @PreAuthorize("@authz.hasPermission('system:user:manage')")
    public ApiResponse<Void> resetPassword(
            @PathVariable String id,
            @Valid @RequestBody UserPasswordResetRequest request
    ) {
        systemUserService.resetPassword(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/registration-review")
    @PreAuthorize("@authz.hasPermission('system:user:manage')")
    public ApiResponse<UserDetail> reviewRegistration(
            @PathVariable String id,
            @Valid @RequestBody RegistrationReviewRequest request
    ) {
        return ApiResponse.ok(systemUserService.reviewRegistration(id, request));
    }
}
