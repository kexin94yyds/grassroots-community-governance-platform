package com.cunzhi.governance.workbench.audit;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.workbench.mapper.OperationAuditMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

public class OperationAuditFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OperationAuditFilter.class);

    private final OperationAuditMapper mapper;

    public OperationAuditFilter(OperationAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return classify(request.getMethod(), request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        AuditTarget target = classify(request.getMethod(), request.getRequestURI());
        Long operatorUserId = currentUserId();
        boolean completed = false;
        try {
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            int status = completed ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            record(operatorUserId, target, request.getMethod(), request.getRequestURI(), status);
        }
    }

    static AuditTarget classify(String requestMethod, String requestPath) {
        String method = requestMethod == null ? "" : requestMethod.toUpperCase(Locale.ROOT);
        String path = requestPath == null ? "" : requestPath;
        if ("GET".equals(method) && isAttachmentDownload(path)) {
            return new AuditTarget("ATTACHMENT", "DOWNLOAD");
        }
        if ("GET".equals(method) || "OPTIONS".equals(method)) {
            return null;
        }
        if (path.equals("/api/auth/password")) {
            return new AuditTarget("SYSTEM_MANAGEMENT", "PASSWORD_CHANGE");
        }
        if (path.startsWith("/api/system/users")) {
            return new AuditTarget("SYSTEM_MANAGEMENT", "USER_MANAGEMENT");
        }
        if (path.startsWith("/api/system/roles")) {
            return new AuditTarget("SYSTEM_MANAGEMENT", "ROLE_MANAGEMENT");
        }
        if (path.startsWith("/api/system/menus")) {
            return new AuditTarget("SYSTEM_MANAGEMENT", "MENU_MANAGEMENT");
        }
        if (path.startsWith("/api/system/event-categories")) {
            return new AuditTarget("SYSTEM_MANAGEMENT", "CATEGORY_MANAGEMENT");
        }
        if (path.equals("/api/grids") || path.startsWith("/api/grids/")) {
            return new AuditTarget("SYSTEM_MANAGEMENT", "GRID_MANAGEMENT");
        }
        return null;
    }

    private static boolean isAttachmentDownload(String path) {
        return path.matches("/api/files/[^/]+")
                || path.matches("/api/tasks/[^/]+/attachments/[^/]+/content")
                || path.matches("/api/resident-portal/events/[^/]+/attachments/[^/]+/content");
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user.id()
                : null;
    }

    private void record(
            Long operatorUserId,
            AuditTarget target,
            String method,
            String path,
            int status
    ) {
        String result = status >= 200 && status < 400 ? "SUCCESS" : "FAILURE";
        try {
            if (mapper.insert(operatorUserId, target.module(), target.action(), method, path, result, status) != 1) {
                log.warn("Operation audit row was not inserted for {} {}", method, path);
            }
        } catch (RuntimeException exception) {
            log.error("Operation audit insert failed for {} {}: {}", method, path, exception.getMessage());
        }
    }

    record AuditTarget(String module, String action) {
    }
}
