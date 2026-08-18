package com.cunzhi.governance.system.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.system.dto.UserStatusRequest;
import com.cunzhi.governance.system.dto.UserPasswordResetRequest;
import com.cunzhi.governance.system.dto.RegistrationReviewRequest;
import com.cunzhi.governance.system.mapper.SystemUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemUserServiceTest {

    @Mock
    private SystemUserMapper mapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private DataScopeService dataScopeService;

    @Test
    void cannotDisableTheLastEnabledSystemAdministrator() {
        when(mapper.findById(2)).thenReturn(new SystemUserMapper.SystemUserDetailRow(
                2L, "admin", "管理员", null, "ENABLED",
                "STAFF", "APPROVED", null, null, null, null, null,
                3, null, "SYSTEM_ADMIN"
        ));
        when(dataScopeService.currentUser()).thenReturn(new AuthenticatedUser(
                1L, "other-admin", "", "另一管理员", true,
                Set.of("SYSTEM_ADMIN"), Set.of("system:user:manage")
        ));
        when(mapper.countOtherEnabledSystemAdmins(2)).thenReturn(0);

        assertThatThrownBy(() -> service().updateStatus("2", new UserStatusRequest(false, 3)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(mapper, never()).updateStatus(2, "DISABLED", 3);
    }

    @Test
    void approvingResidentRegistrationBindsArchiveAndAssignsOnlyResidentRole() {
        var pending = new SystemUserMapper.SystemUserDetailRow(
                9L, "resident.li", "李居民", "13800000000", "DISABLED",
                "RESIDENT", "PENDING", 31L, "李居民", null, null, null,
                2, null, null
        );
        var approved = new SystemUserMapper.SystemUserDetailRow(
                9L, "resident.li", "李居民", "13800000000", "ENABLED",
                "RESIDENT", "APPROVED", 31L, "李居民", null, null, null,
                3, null, "RESIDENT"
        );
        when(mapper.findById(9)).thenReturn(pending, approved);
        when(dataScopeService.currentUser()).thenReturn(new AuthenticatedUser(
                1L, "admin", "", "管理员", true,
                Set.of("SYSTEM_ADMIN"), Set.of("system:user:manage")
        ));
        when(mapper.countAvailableResident(31)).thenReturn(1);
        when(mapper.countEnabledRoles(java.util.List.of("RESIDENT"))).thenReturn(1);
        when(mapper.linkResidentUser(31, 9)).thenReturn(1);
        when(mapper.reviewRegistration(9, "APPROVED", "ENABLED", 1, null, 2)).thenReturn(1);

        var result = service().reviewRegistration(
                "9", new RegistrationReviewRequest("APPROVE", Set.of(), null, 2)
        );

        assertThat(result.approvalStatus()).isEqualTo("APPROVED");
        assertThat(result.roles()).containsExactly("RESIDENT");
        verify(mapper).linkResidentUser(31, 9);
        verify(mapper).insertUserRole(9, "RESIDENT");
    }

    @Test
    void cannotEnableResidentAccountWhenLinkedResidentIsNotActive() {
        var residentUser = new SystemUserMapper.SystemUserDetailRow(
                9L, "resident.li", "李居民", null, "DISABLED",
                "RESIDENT", "APPROVED", 31L, "李居民", null, null, null,
                3, null, "RESIDENT"
        );
        when(mapper.findById(9)).thenReturn(residentUser);
        when(mapper.countActiveLinkedResident(9)).thenReturn(0);

        assertThatThrownBy(() -> service().updateStatus("9", new UserStatusRequest(true, 3)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(mapper, never()).updateStatus(9, "ENABLED", 3);
    }

    @Test
    void administratorResetStoresOnlyHashAndForcesPasswordChange() {
        var user = new SystemUserMapper.SystemUserDetailRow(
                12L, "worker", "网格员", null, "ENABLED",
                "STAFF", "APPROVED", null, null, null, null, null,
                5, null, "GRID_WORKER"
        );
        when(mapper.findById(12)).thenReturn(user);
        when(dataScopeService.currentUser()).thenReturn(new AuthenticatedUser(
                1L, "admin", "", "管理员", true,
                Set.of("SYSTEM_ADMIN"), Set.of("system:user:manage")
        ));
        when(passwordEncoder.encode("Temporary9!")).thenReturn("temporary-hash");
        when(mapper.resetPassword(12, "temporary-hash", 5)).thenReturn(1);

        service().resetPassword("12", new UserPasswordResetRequest("Temporary9!", 5));

        verify(mapper).resetPassword(12, "temporary-hash", 5);
    }

    private SystemUserService service() {
        return new SystemUserService(mapper, passwordEncoder, dataScopeService);
    }
}
