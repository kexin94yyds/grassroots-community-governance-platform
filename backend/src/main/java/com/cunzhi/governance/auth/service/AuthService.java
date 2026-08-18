package com.cunzhi.governance.auth.service;

import com.cunzhi.governance.auth.dto.CurrentUserResponse;
import com.cunzhi.governance.auth.dto.NavigationItem;
import com.cunzhi.governance.auth.dto.LoginRequest;
import com.cunzhi.governance.auth.dto.PasswordChangeRequest;
import com.cunzhi.governance.auth.mapper.UserAuthMapper;
import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CsrfTokenRepository csrfTokenRepository;
    private final UserAuthMapper userAuthMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            CsrfTokenRepository csrfTokenRepository,
            UserAuthMapper userAuthMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.csrfTokenRepository = csrfTokenRepository;
        this.userAuthMapper = userAuthMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public CurrentUserResponse login(
            LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        Authentication authenticationRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password());
        Authentication authentication = authenticationManager.authenticate(authenticationRequest);
        CurrentUserResponse currentUser = currentUser(authentication);
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        userAuthMapper.updateLastLoginAt(authenticatedUser.id());

        sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return currentUser;
    }

    public CurrentUserResponse currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return CurrentUserResponse.from(user);
    }

    @Transactional
    public void changePassword(PasswordChangeRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        UserAuthMapper.UserAccountRow account = userAuthMapper.findByUsername(user.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (!passwordEncoder.matches(request.oldPassword(), account.passwordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), account.passwordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "新密码不能与原密码相同");
        }
        if (userAuthMapper.updateOwnPassword(
                user.id(), passwordEncoder.encode(request.newPassword()), user.securityVersion()
        ) != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    public List<NavigationItem> navigation(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userAuthMapper.findEnabledNavigationMenus(user.id()).stream()
                .map(row -> new NavigationItem(
                        row.id().toString(), row.code(), row.name(), row.routePath(), row.icon(), row.sortNo()
                ))
                .toList();
    }

    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        csrfTokenRepository.saveToken(null, request, response);
    }
}
