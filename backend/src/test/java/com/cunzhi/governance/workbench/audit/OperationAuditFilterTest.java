package com.cunzhi.governance.workbench.audit;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.workbench.mapper.OperationAuditMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationAuditFilterTest {

    @Mock
    private OperationAuditMapper mapper;
    @Mock
    private FilterChain chain;

    @BeforeEach
    void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(
                9L, "admin", "hash", "系统管理员", true,
                Set.of("SYSTEM_ADMIN"), Set.of("system:audit:read")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsSuccessfulResidentAttachmentDownload() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/resident-portal/events/12/attachments/34/content"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(mapper.insert(
                9L, "ATTACHMENT", "DOWNLOAD", "GET",
                "/api/resident-portal/events/12/attachments/34/content", "SUCCESS", 200
        )).thenReturn(1);

        new OperationAuditFilter(mapper).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(mapper).insert(
                9L, "ATTACHMENT", "DOWNLOAD", "GET",
                "/api/resident-portal/events/12/attachments/34/content", "SUCCESS", 200
        );
    }

    @Test
    void recordsFailedKeyManagementResult() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/system/users/18/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        doAnswer(invocation -> {
            ((MockHttpServletResponse) invocation.getArgument(1)).setStatus(409);
            return null;
        }).when(chain).doFilter(any(), any());
        when(mapper.insert(
                9L, "SYSTEM_MANAGEMENT", "USER_MANAGEMENT", "PATCH",
                "/api/system/users/18/status", "FAILURE", 409
        )).thenReturn(1);

        new OperationAuditFilter(mapper).doFilter(request, response, chain);

        verify(mapper).insert(
                9L, "SYSTEM_MANAGEMENT", "USER_MANAGEMENT", "PATCH",
                "/api/system/users/18/status", "FAILURE", 409
        );
    }

    @Test
    void ignoresOrdinaryReadRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/overview");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OperationAuditFilter(mapper).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(mapper);
    }
}
