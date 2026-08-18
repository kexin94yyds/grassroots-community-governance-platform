package com.cunzhi.governance.auth.controller;

import com.cunzhi.governance.auth.dto.CsrfTokenResponse;
import com.cunzhi.governance.auth.dto.CurrentUserResponse;
import com.cunzhi.governance.auth.dto.LoginRequest;
import com.cunzhi.governance.auth.dto.PasswordChangeRequest;
import com.cunzhi.governance.auth.dto.NavigationItem;
import com.cunzhi.governance.auth.dto.RegistrationRequest;
import com.cunzhi.governance.auth.dto.RegistrationResponse;
import com.cunzhi.governance.auth.service.AuthService;
import com.cunzhi.governance.auth.service.RegistrationService;
import com.cunzhi.governance.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;

    public AuthController(AuthService authService, RegistrationService registrationService) {
        this.authService = authService;
        this.registrationService = registrationService;
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ApiResponse.ok(CsrfTokenResponse.from(csrfToken));
    }

    @PostMapping("/login")
    public ApiResponse<CurrentUserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        return ApiResponse.ok(authService.login(request, servletRequest, servletResponse));
    }

    @PostMapping("/register")
    public ApiResponse<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        return ApiResponse.ok(registrationService.register(request));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(Authentication authentication) {
        return ApiResponse.ok(authService.currentUser(authentication));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            Authentication authentication
    ) {
        authService.changePassword(request, authentication);
        return ApiResponse.ok();
    }

    @GetMapping("/navigation")
    public ApiResponse<List<NavigationItem>> navigation(Authentication authentication) {
        return ApiResponse.ok(authService.navigation(authentication));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        authService.logout(request, response, authentication);
        return ApiResponse.ok();
    }
}
