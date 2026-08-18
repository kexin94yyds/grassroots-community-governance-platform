package com.cunzhi.governance.auth.service;

import com.cunzhi.governance.auth.dto.PasswordChangeRequest;
import com.cunzhi.governance.auth.mapper.UserAuthMapper;
import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock SecurityContextRepository securityContextRepository;
    @Mock SessionAuthenticationStrategy sessionAuthenticationStrategy;
    @Mock CsrfTokenRepository csrfTokenRepository;
    @Mock UserAuthMapper userAuthMapper;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void changesPasswordAfterVerifyingOldPasswordAndInvalidatesSessions() {
        AuthenticatedUser user = user();
        when(userAuthMapper.findByUsername("staff")).thenReturn(Optional.of(account()));
        when(passwordEncoder.matches("OldPassword1!", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword2!", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword2!")).thenReturn("new-hash");
        when(userAuthMapper.updateOwnPassword(9, "new-hash", 4)).thenReturn(1);

        service().changePassword(
                new PasswordChangeRequest("OldPassword1!", "NewPassword2!"),
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities())
        );

        verify(userAuthMapper).updateOwnPassword(9, "new-hash", 4);
    }

    @Test
    void rejectsWrongOldPasswordWithoutChangingHash() {
        AuthenticatedUser user = user();
        when(userAuthMapper.findByUsername("staff")).thenReturn(Optional.of(account()));
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service().changePassword(
                new PasswordChangeRequest("wrong-password", "NewPassword2!"),
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities())
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(userAuthMapper, never()).updateOwnPassword(9, "new-hash", 4);
    }

    private AuthService service() {
        return new AuthService(authenticationManager, securityContextRepository,
                sessionAuthenticationStrategy, csrfTokenRepository, userAuthMapper, passwordEncoder);
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(9L, "staff", "old-hash", "工作人员", true, 4,
                false, Set.of("COMMUNITY_STAFF"), Set.of("grid:read"));
    }

    private UserAuthMapper.UserAccountRow account() {
        return new UserAuthMapper.UserAccountRow(
                9L, "staff", "old-hash", "工作人员", "ENABLED", 4, false
        );
    }
}
