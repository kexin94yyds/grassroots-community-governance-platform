package com.cunzhi.governance.auth.service;

import com.cunzhi.governance.auth.dto.RegistrationRequest;
import com.cunzhi.governance.auth.mapper.RegistrationMapper;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.resident.service.SensitiveDataCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private RegistrationMapper mapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SensitiveDataCodec codec;

    @Test
    void staffRegistrationCreatesDisabledPendingAccountWithoutRoles() {
        when(passwordEncoder.encode("StrongPass9")).thenReturn("hash");

        var response = service().register(new RegistrationRequest(
                "STAFF", "worker.apply", "StrongPass9", "申请人", "13800000000", null, "第一社区"
        ));

        assertThat(response.approvalStatus()).isEqualTo("PENDING");
        verify(mapper).insertPendingUser(
                "worker.apply", "hash", "申请人", "13800000000", "STAFF", null, "第一社区"
        );
    }

    @Test
    void residentRegistrationMatchesUnboundArchiveByIrreversibleFingerprints() {
        when(codec.fingerprint("110101199001011234")).thenReturn("id-hash");
        when(codec.fingerprint("13800000000")).thenReturn("phone-hash");
        when(mapper.findAvailableResidentId("王居民", "id-hash", "phone-hash")).thenReturn(19L);
        when(passwordEncoder.encode("StrongPass9")).thenReturn("hash");

        service().register(new RegistrationRequest(
                "RESIDENT", "resident.wang", "StrongPass9", "王居民", "13800000000",
                "110101199001011234", null
        ));

        verify(mapper).insertPendingUser(
                "resident.wang", "hash", "王居民", null, "RESIDENT", 19L, null
        );
    }

    @Test
    void residentRegistrationNormalizesPhoneBeforeMatchingAndDoesNotPersistPlaintext() {
        when(codec.fingerprint("110101199001011234")).thenReturn("id-hash");
        when(codec.fingerprint("13800000000")).thenReturn("phone-hash");
        when(mapper.findAvailableResidentId("王居民", "id-hash", "phone-hash")).thenReturn(19L);
        when(passwordEncoder.encode("StrongPass9")).thenReturn("hash");

        service().register(new RegistrationRequest(
                "RESIDENT", "resident.safe", "StrongPass9", "王居民", "138-0000-0000",
                "110101199001011234", null
        ));

        verify(codec).fingerprint("13800000000");
        verify(mapper).insertPendingUser(
                "resident.safe", "hash", "王居民", null, "RESIDENT", 19L, null
        );
    }

    @Test
    void residentRegistrationDoesNotRevealWhichIdentityFieldFailed() {
        when(codec.fingerprint("110101199001011234")).thenReturn("id-hash");
        when(codec.fingerprint("13800000000")).thenReturn("phone-hash");

        assertThatThrownBy(() -> service().register(new RegistrationRequest(
                "RESIDENT", "resident.wang", "StrongPass9", "王居民", "13800000000",
                "110101199001011234", null
        ))).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            assertThat(exception.getMessage()).contains("未匹配到可绑定档案");
        });
    }

    private RegistrationService service() {
        return new RegistrationService(mapper, passwordEncoder, codec);
    }
}
