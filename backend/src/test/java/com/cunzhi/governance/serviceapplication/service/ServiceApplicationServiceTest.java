package com.cunzhi.governance.serviceapplication.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.resident.mapper.ResidentMapper;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationActionRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationCreateRequest;
import com.cunzhi.governance.serviceapplication.mapper.ServiceApplicationFlowMapper;
import com.cunzhi.governance.serviceapplication.mapper.ServiceApplicationMapper;
import com.cunzhi.governance.serviceapplication.mapper.ServiceCatalogMapper;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.service.DataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceApplicationServiceTest {

    @Mock private ServiceApplicationMapper applicationMapper;
    @Mock private ServiceApplicationFlowMapper flowMapper;
    @Mock private ServiceCatalogMapper catalogMapper;
    @Mock private ResidentMapper residentMapper;
    @Mock private DataScopeMapper dataScopeMapper;
    @Mock private DataScopeService dataScopeService;
    @Mock private BusinessNumberGenerator numberGenerator;

    @Test
    void communityStaffCanRejectSubmittedApplicationBeforeItHasAHandler() {
        when(dataScopeService.currentUser()).thenReturn(communityStaff(5L));
        when(applicationMapper.findByIdForUpdate(9L)).thenReturn(lock("SUBMITTED", null, 0));
        when(dataScopeService.scopeForRole("COMMUNITY_STAFF"))
                .thenReturn(new DataScope(DataScopeType.COMMUNITY, Set.of(7L)));
        when(applicationMapper.transition(9L, "SUBMITTED", "REJECTED", null, null, 0)).thenReturn(1);
        when(flowMapper.insert(9L, "REJECT", "SUBMITTED", "REJECTED", 5L, "材料不完整")).thenReturn(1);
        when(applicationMapper.findById(9L)).thenReturn(Optional.of(application("REJECTED", null, 1)));

        var result = service().reject("9", new ServiceApplicationActionRequest(0, null, "材料不完整"));

        assertThat(result.status()).isEqualTo("REJECTED");
        verify(dataScopeMapper, never()).lockActiveCommunityStaffAssignmentForUpdate(5L, 3L);
    }

    @Test
    void acceptanceLocksCommunityAndActiveAssignmentBeforeAssigningHandler() {
        when(dataScopeService.currentUser()).thenReturn(communityStaff(5L));
        when(applicationMapper.findByIdForUpdate(9L)).thenReturn(lock("SUBMITTED", null, 0));
        when(applicationMapper.findById(9L)).thenReturn(
                Optional.of(application("SUBMITTED", null, 0)),
                Optional.of(application("ACCEPTED", 5L, 1))
        );
        when(dataScopeMapper.findParentCommunityId(7L)).thenReturn(3L);
        when(dataScopeMapper.lockEnabledCommunityForUpdate(3L)).thenReturn(3L);
        when(dataScopeMapper.lockActiveCommunityStaffAssignmentForUpdate(5L, 3L)).thenReturn(18L);
        when(applicationMapper.transition(9L, "SUBMITTED", "ACCEPTED", 5L, null, 0)).thenReturn(1);
        when(flowMapper.insert(9L, "ACCEPT", "SUBMITTED", "ACCEPTED", 5L, null)).thenReturn(1);

        var result = service().accept("9", new ServiceApplicationActionRequest(0, null, null));

        assertThat(result.handlerUserId()).isEqualTo("5");
        InOrder order = inOrder(applicationMapper, dataScopeMapper);
        order.verify(applicationMapper).findById(9L);
        order.verify(dataScopeMapper).findParentCommunityId(7L);
        order.verify(dataScopeMapper).lockEnabledCommunityForUpdate(3L);
        order.verify(dataScopeMapper).lockActiveCommunityStaffAssignmentForUpdate(5L, 3L);
        order.verify(applicationMapper).findByIdForUpdate(9L);
        order.verify(applicationMapper).transition(9L, "SUBMITTED", "ACCEPTED", 5L, null, 0);
    }

    @Test
    void residentApplyLocksEnabledCatalogBeforeCreatingApplication() {
        String token = "123e4567-e89b-12d3-a456-426614174000";
        when(dataScopeService.currentUser()).thenReturn(residentUser(21L));
        when(residentMapper.findByUserId(21L)).thenReturn(Optional.of(resident()));
        when(catalogMapper.findEnabledByIdForUpdate(2L)).thenReturn(catalog());
        when(numberGenerator.next("SVC")).thenReturn("SVC0001");
        when(applicationMapper.insert("SVC0001", 1L, 21L, 7L, 2L, "需要上门协助", null, token)).thenReturn(1);
        when(applicationMapper.findIdByApplicationNo("SVC0001")).thenReturn(9L);
        when(flowMapper.insert(9L, "APPLY", null, "SUBMITTED", 21L, "提交服务申请")).thenReturn(1);
        when(applicationMapper.findById(9L)).thenReturn(Optional.of(application("SUBMITTED", null, 0)));

        var result = service().apply(new ServiceApplicationCreateRequest("2", "需要上门协助", null, token));

        assertThat(result.applicationNo()).isEqualTo("SVC0001");
        InOrder order = inOrder(catalogMapper, applicationMapper);
        order.verify(catalogMapper).findEnabledByIdForUpdate(2L);
        order.verify(applicationMapper).insert("SVC0001", 1L, 21L, 7L, 2L, "需要上门协助", null, token);
    }

    private ServiceApplicationService service() {
        return new ServiceApplicationService(
                applicationMapper, flowMapper, catalogMapper, residentMapper,
                dataScopeMapper, dataScopeService, numberGenerator
        );
    }

    private AuthenticatedUser communityStaff(long id) {
        return new AuthenticatedUser(id, "staff", "", "社区人员", true,
                Set.of("COMMUNITY_STAFF"), Set.of("service:application:read", "service:application:handle"));
    }

    private AuthenticatedUser residentUser(long id) {
        return new AuthenticatedUser(id, "resident", "", "居民", true,
                Set.of("RESIDENT"), Set.of("resident:portal", "service:application:apply"));
    }

    private ServiceApplicationMapper.ApplicationLockRow lock(String status, Long handlerUserId, int version) {
        return new ServiceApplicationMapper.ApplicationLockRow(9L, 1L, 21L, 7L, status, handlerUserId, null, version);
    }

    private ServiceApplicationMapper.ApplicationRow application(String status, Long handlerUserId, int version) {
        return new ServiceApplicationMapper.ApplicationRow(
                9L, "SVC0001", 1L, "居民甲", 21L, 7L, "第一网格", 2L, "养老关怀",
                "需要上门协助", null, status, handlerUserId,
                handlerUserId == null ? null : "社区人员", null, null, null,
                LocalDateTime.now(), null, version
        );
    }

    private ResidentMapper.ResidentRow resident() {
        return new ResidentMapper.ResidentRow(
                1L, "RES0001", 7L, "第一网格", null, null, "居民甲", null, null,
                null, null, "测试地址", false, "[]", null, "ACTIVE", 0
        );
    }

    private ServiceCatalogMapper.CatalogRow catalog() {
        return new ServiceCatalogMapper.CatalogRow(2L, "ELDERLY_CARE", "养老关怀", null, 10, "ENABLED", 0);
    }
}
