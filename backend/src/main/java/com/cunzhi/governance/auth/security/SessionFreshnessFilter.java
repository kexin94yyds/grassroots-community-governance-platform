package com.cunzhi.governance.auth.security;

import com.cunzhi.governance.auth.mapper.UserAuthMapper;
import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
public class SessionFreshnessFilter extends OncePerRequestFilter {

    private final UserAuthMapper userAuthMapper;
    private final CsrfTokenRepository csrfTokenRepository;

    public SessionFreshnessFilter(
            UserAuthMapper userAuthMapper,
            CsrfTokenRepository csrfTokenRepository
    ) {
        this.userAuthMapper = userAuthMapper;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            UserAuthMapper.SessionStateRow state = userAuthMapper.findSessionState(user.id());
            if (state == null
                    || !"ENABLED".equals(state.status())
                    || state.securityVersion() != user.securityVersion()) {
                invalidate(request, response);
                writeUnauthenticated(response);
                return;
            }
            if (state.passwordChangeRequired() && !isPasswordLifecycleRequest(request)) {
                writePasswordChangeRequired(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPasswordLifecycleRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/auth/csrf".equals(path)
                || "/api/auth/me".equals(path)
                || "/api/auth/password".equals(path)
                || "/api/auth/logout".equals(path);
    }

    private void writePasswordChangeRequired(HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.PASSWORD_CHANGE_REQUIRED;
        response.setStatus(code.status().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"code":"%s","message":"%s","data":null,"timestamp":"%s"}
                """.formatted(code.code(), code.defaultMessage(), OffsetDateTime.now(ZoneId.of("Asia/Shanghai"))));
    }

    private void invalidate(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        csrfTokenRepository.saveToken(null, request, response);
    }

    private void writeUnauthenticated(HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.UNAUTHENTICATED;
        response.setStatus(code.status().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"code":"%s","message":"账号权限已变更，请重新登录","data":null,"timestamp":"%s"}
                """.formatted(code.code(), OffsetDateTime.now(ZoneId.of("Asia/Shanghai"))));
    }
}
