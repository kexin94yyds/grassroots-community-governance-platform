package com.cunzhi.governance.auth.security;

import com.cunzhi.governance.auth.mapper.UserAuthMapper;
import com.cunzhi.governance.auth.model.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionFreshnessFilterTest {

    @Mock
    private UserAuthMapper mapper;
    @Mock
    private CsrfTokenRepository csrfTokenRepository;
    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidatesSessionWhenSecurityVersionChanges() throws Exception {
        authenticate(2);
        when(mapper.findSessionState(9)).thenReturn(new UserAuthMapper.SessionStateRow("ENABLED", 3, false));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("请重新登录");
        verify(filterChain, never()).doFilter(request, response);
        verify(csrfTokenRepository).saveToken(null, request, response);
    }

    @Test
    void continuesWhenSessionSecurityVersionIsCurrent() throws Exception {
        authenticate(2);
        when(mapper.findSessionState(9)).thenReturn(new UserAuthMapper.SessionStateRow("ENABLED", 2, false));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksOrdinaryRequestsUntilRequiredPasswordChangeCompletes() throws Exception {
        authenticate(2);
        when(mapper.findSessionState(9)).thenReturn(new UserAuthMapper.SessionStateRow("ENABLED", 2, true));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED");
        verify(filterChain, never()).doFilter(request, response);
    }

    private void authenticate(long securityVersion) {
        AuthenticatedUser user = new AuthenticatedUser(
                9L, "staff", "", "工作人员", true, securityVersion,
                Set.of("COMMUNITY_STAFF"), Set.of("grid:read")
        );
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities())
        );
    }

    private SessionFreshnessFilter filter() {
        return new SessionFreshnessFilter(mapper, csrfTokenRepository);
    }
}
