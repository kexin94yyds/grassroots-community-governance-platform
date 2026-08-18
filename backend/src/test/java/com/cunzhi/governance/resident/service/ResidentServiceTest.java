package com.cunzhi.governance.resident.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.resident.dto.ResidentSensitiveSearchRequest;
import com.cunzhi.governance.resident.dto.ResidentSensitiveViewRequest;
import com.cunzhi.governance.resident.mapper.HouseholdMapper;
import com.cunzhi.governance.resident.mapper.ResidentMapper;
import com.cunzhi.governance.resident.mapper.ResidentSensitiveAccessMapper;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.mapper.SystemUserMapper;
import com.cunzhi.governance.resident.dto.ResidentStatusRequest;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.security.PermissionCodes;
import com.cunzhi.governance.system.service.DataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResidentServiceTest {

    @Mock
    private ResidentMapper residentMapper;
    @Mock
    private ResidentSensitiveAccessMapper sensitiveAccessMapper;
    @Mock
    private HouseholdMapper householdMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private DataScopeMapper dataScopeMapper;
    @Mock
    private SystemUserMapper systemUserMapper;
    @Mock
    private BusinessNumberGenerator numberGenerator;
    @Mock
    private SensitiveDataCodec sensitiveDataCodec;
    @Mock
    private ObjectMapper objectMapper;

    @Test
    void exactPhoneSearchUsesFingerprintScopeAndMaskedResultsOnly() {
        AuthenticatedUser user = sensitiveUser();
        when(dataScopeService.currentUser()).thenReturn(user);
        when(dataScopeService.currentScope()).thenReturn(DataScope.all());
        when(sensitiveDataCodec.fingerprint("13800000000")).thenReturn("phone-hash");
        when(residentMapper.findPage(
                null, null, "phone-hash", null, "ACTIVE", true, List.of(), 0, 20
        )).thenReturn(List.of());
        when(residentMapper.count(
                null, null, "phone-hash", null, "ACTIVE", true, List.of()
        )).thenReturn(0L);
        when(sensitiveAccessMapper.insert(9L, null, null, "SEARCH", "PHONE", null, 0)).thenReturn(1);

        var result = service().findBySensitiveValue(new ResidentSensitiveSearchRequest(
                "PHONE", "138-0000 0000", null, "ACTIVE", 1, 20
        ));

        assertThat(result.total()).isZero();
        verify(sensitiveDataCodec).fingerprint("13800000000");
        verify(sensitiveAccessMapper).insert(9L, null, null, "SEARCH", "PHONE", null, 0);
    }

    @Test
    void explicitSensitiveViewDecryptsAfterScopeCheckAndAuditsPurpose() {
        byte[] idCiphertext = {1, 2};
        byte[] phoneCiphertext = {3, 4};
        when(dataScopeService.currentUser()).thenReturn(sensitiveUser());
        when(residentMapper.findById(15L)).thenReturn(Optional.of(residentRow()));
        when(residentMapper.findSensitiveById(15L)).thenReturn(Optional.of(
                new ResidentMapper.ResidentSensitiveRow(15L, 7L, idCiphertext, phoneCiphertext)
        ));
        when(sensitiveDataCodec.decrypt(idCiphertext)).thenReturn("110101199001011234");
        when(sensitiveDataCodec.decrypt(phoneCiphertext)).thenReturn("13800000000");
        when(sensitiveAccessMapper.insert(
                9L, 15L, 7L, "VIEW", "BOTH", "核验居民身份资料", 1
        )).thenReturn(1);

        var result = service().viewSensitive(
                "15", new ResidentSensitiveViewRequest(" 核验居民身份资料 ")
        );

        assertThat(result.idCard()).isEqualTo("110101199001011234");
        assertThat(result.phone()).isEqualTo("13800000000");
        verify(dataScopeService).requireGridAccess(7L);
        verify(sensitiveAccessMapper).insert(
                9L, 15L, 7L, "VIEW", "BOTH", "核验居民身份资料", 1
        );
    }

    @Test
    void sensitiveOperationRejectsUserWithoutPermissionBeforeDataAccess() {
        when(dataScopeService.currentUser()).thenReturn(new AuthenticatedUser(
                8L, "worker", "hash", "网格员", true, Set.of("GRID_WORKER"), Set.of("resident:read")
        ));

        assertThatThrownBy(() -> service().findBySensitiveValue(new ResidentSensitiveSearchRequest(
                "PHONE", "13800000000", null, null, 1, 20
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(residentMapper, sensitiveAccessMapper, sensitiveDataCodec);
    }

    @Test
    void sensitiveAuditQueryUsesCurrentDataScopeAndNeverMapsSecretColumns() {
        when(dataScopeService.currentUser()).thenReturn(auditUser());
        when(dataScopeService.currentScope()).thenReturn(new DataScope(DataScopeType.COMMUNITY, Set.of(7L)));
        LocalDateTime accessedAt = LocalDateTime.of(2026, 8, 8, 11, 0);
        when(sensitiveAccessMapper.findPage(
                "VIEW", "BOTH", "王", false, List.of(7L), 9L, 0, 20
        )).thenReturn(List.of(new ResidentSensitiveAccessMapper.AccessLogRow(
                31L, 9L, "社区人员", "staff", 15L, "RES0015", "王居民",
                7L, "GRD-007", "第七网格", "VIEW", "BOTH", "办理补贴身份核验", 1, accessedAt
        )));
        when(sensitiveAccessMapper.countPage(
                "VIEW", "BOTH", "王", false, List.of(7L), 9L
        )).thenReturn(1L);

        var page = service().findSensitiveAccessLogs("view", "both", " 王 ", 1, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("31");
            assertThat(item.residentName()).isEqualTo("王*");
            assertThat(item.residentNo()).isEqualTo("RE***15");
            assertThat(item.residentNo()).doesNotContain("RES0015");
            assertThat(item.scopeGridId()).isEqualTo("7");
            assertThat(item.operatorName()).isEqualTo("社区人员");
            assertThat(item.createdAt()).isEqualTo(accessedAt);
        });
        verify(sensitiveAccessMapper).findPage("VIEW", "BOTH", "王", false, List.of(7L), 9L, 0, 20);
        verify(sensitiveAccessMapper).countPage("VIEW", "BOTH", "王", false, List.of(7L), 9L);
    }

    @Test
    void movingResidentDisablesLinkedAccountInSameServiceOperation() {
        ResidentMapper.ResidentRow active = residentRow();
        ResidentMapper.ResidentRow moved = new ResidentMapper.ResidentRow(
                active.id(), active.residentNo(), active.gridId(), active.gridName(),
                active.householdId(), active.householdNo(), active.realName(), active.gender(),
                active.birthDate(), active.idCardLast4(), active.phoneLast4(), active.address(),
                active.householder(), null, active.remark(), "MOVED", 1
        );
        when(residentMapper.findById(15L)).thenReturn(Optional.of(active), Optional.of(moved));
        when(residentMapper.findLinkedUserId(15L)).thenReturn(22L);
        when(systemUserMapper.disableLinkedResidentUser(22L)).thenReturn(1);
        when(residentMapper.updateStatus(15L, "MOVED", 0)).thenReturn(1);

        var result = service().updateStatus("15", new ResidentStatusRequest("MOVED", 0));

        assertThat(result.status()).isEqualTo("MOVED");
        verify(systemUserMapper).disableLinkedResidentUser(22L);
        verify(residentMapper).updateStatus(15L, "MOVED", 0);
    }

    private ResidentService service() {
        return new ResidentService(
                residentMapper,
                sensitiveAccessMapper,
                householdMapper,
                dataScopeService,
                dataScopeMapper,
                systemUserMapper,
                numberGenerator,
                sensitiveDataCodec,
                objectMapper
        );
    }

    private AuthenticatedUser sensitiveUser() {
        return new AuthenticatedUser(
                9L,
                "staff",
                "hash",
                "社区人员",
                true,
                Set.of("COMMUNITY_STAFF"),
                Set.of("resident:read", PermissionCodes.RESIDENT_SENSITIVE_READ)
        );
    }

    private AuthenticatedUser auditUser() {
        return new AuthenticatedUser(
                9L,
                "staff",
                "hash",
                "社区人员",
                true,
                Set.of("COMMUNITY_STAFF"),
                Set.of("resident:read", PermissionCodes.RESIDENT_SENSITIVE_AUDIT_READ)
        );
    }

    private ResidentMapper.ResidentRow residentRow() {
        return new ResidentMapper.ResidentRow(
                15L,
                "RES0015",
                7L,
                "第一网格",
                null,
                null,
                "王居民",
                "UNKNOWN",
                LocalDate.of(1990, 1, 1),
                "1234",
                "0000",
                "测试地址",
                false,
                "[]",
                null,
                "ACTIVE",
                0
        );
    }
}
