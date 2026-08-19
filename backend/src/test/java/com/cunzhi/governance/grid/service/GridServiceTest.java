package com.cunzhi.governance.grid.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.grid.dto.GridAssignmentsRequest;
import com.cunzhi.governance.grid.mapper.GridMapper;
import com.cunzhi.governance.task.mapper.TaskMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.service.DataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GridServiceTest {

    @Mock
    private GridMapper gridMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private BusinessNumberGenerator numberGenerator;
    @Mock
    private ObjectMapper objectMapper;

    @Test
    void requiresExactlyOnePrimaryWorkerBeforeChangingAnything() {
        when(gridMapper.lockArea(9)).thenReturn(9L);
        when(gridMapper.findById(9)).thenReturn(grid());
        when(dataScopeService.currentUser()).thenReturn(user());

        assertThatThrownBy(() -> service().replaceAssignments(
                "9",
                new GridAssignmentsRequest(2, List.of(
                        new GridAssignmentsRequest.Assignment("12", false),
                        new GridAssignmentsRequest.Assignment("13", false)
                ))
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(gridMapper, never()).touchVersion(9, 2);
        verify(gridMapper, never()).endActiveAssignments(9, "GRID_WORKER");
    }

    @Test
    void endsOldAssignmentsAndInsertsNewOnSuccessfulReplacement() {
        when(gridMapper.lockArea(9)).thenReturn(9L);
        when(gridMapper.findById(9)).thenReturn(grid());
        when(dataScopeService.currentUser()).thenReturn(user());
        when(gridMapper.countEnabledAssignee(12, "GRID_WORKER")).thenReturn(1);
        when(gridMapper.touchVersion(9, 2)).thenReturn(1);
        when(gridMapper.findActiveAssignmentsForUpdate(9, "GRID_WORKER")).thenReturn(List.of());
        when(gridMapper.findActiveAssignments(9, "GRID_WORKER")).thenReturn(List.of());

        service().replaceAssignments(
                "9",
                new GridAssignmentsRequest(2, List.of(
                        new GridAssignmentsRequest.Assignment("12", true)
                ))
        );

        verify(gridMapper).endActiveAssignments(9, "GRID_WORKER");
        verify(gridMapper).insertAssignment(9, 12, "GRID_WORKER", true, 5);
    }

    @Test
    void systemAdministratorCanAssignCommunityStaffWithOnePrimary() {
        when(gridMapper.lockArea(3)).thenReturn(3L);
        when(gridMapper.findById(3)).thenReturn(community());
        when(dataScopeService.currentUser()).thenReturn(admin());
        when(gridMapper.countEnabledAssignee(21, "COMMUNITY_STAFF")).thenReturn(1);
        when(gridMapper.touchVersion(3, 4)).thenReturn(1);
        when(gridMapper.findActiveAssignmentsForUpdate(3, "COMMUNITY_STAFF")).thenReturn(List.of());
        when(gridMapper.findActiveAssignments(3, "COMMUNITY_STAFF")).thenReturn(List.of());

        service().replaceAssignments(
                "3",
                new GridAssignmentsRequest(4, List.of(
                        new GridAssignmentsRequest.Assignment("21", true)
                ))
        );

        verify(gridMapper).endActiveAssignments(3, "COMMUNITY_STAFF");
        verify(gridMapper).insertAssignment(3, 21, "COMMUNITY_STAFF", true, 1);
        var lockOrder = inOrder(gridMapper);
        lockOrder.verify(gridMapper).lockArea(3L);
        lockOrder.verify(gridMapper).findActiveAssignmentsForUpdate(3L, "COMMUNITY_STAFF");
    }

    @Test
    void communityStaffCannotReplaceTheirOwnCommunityAssignments() {
        when(gridMapper.lockArea(3)).thenReturn(3L);
        when(gridMapper.findById(3)).thenReturn(community());
        when(dataScopeService.currentUser()).thenReturn(user());

        assertThatThrownBy(() -> service().replaceAssignments(
                "3",
                new GridAssignmentsRequest(4, List.of(
                        new GridAssignmentsRequest.Assignment("21", true)
                ))
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(gridMapper, never()).touchVersion(3, 4);
    }

    @Test
    void rejectsRemovingWorkerWithUnfinishedTaskBeforeChangingAssignments() {
        when(gridMapper.lockArea(9)).thenReturn(9L);
        when(gridMapper.findById(9)).thenReturn(grid());
        when(dataScopeService.currentUser()).thenReturn(user());
        when(gridMapper.countEnabledAssignee(13, "GRID_WORKER")).thenReturn(1);
        when(gridMapper.findActiveAssignmentsForUpdate(9, "GRID_WORKER")).thenReturn(List.of(
                new GridMapper.AssignmentRow(12L, "old-worker", "原网格员", true)
        ));
        when(taskMapper.countUnfinishedByGridAndAssignee(9, 12)).thenReturn(1);

        assertThatThrownBy(() -> service().replaceAssignments(
                "9",
                new GridAssignmentsRequest(2, List.of(
                        new GridAssignmentsRequest.Assignment("13", true)
                ))
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(gridMapper, never()).touchVersion(9, 2);
        verify(gridMapper, never()).endActiveAssignments(9, "GRID_WORKER");
    }

    @Test
    void passesCommunityTypeAndScopedGridIdsToListQueries() {
        when(dataScopeService.currentScope()).thenReturn(
                new DataScope(DataScopeType.COMMUNITY, Set.of(9L))
        );
        when(gridMapper.findPage("COMMUNITY", null, null, false, List.of(9L), 0, 20))
                .thenReturn(List.of());
        when(gridMapper.count("COMMUNITY", null, null, false, List.of(9L)))
                .thenReturn(0L);

        service().findPage("COMMUNITY", null, null, 1, 20);

        verify(gridMapper).findPage("COMMUNITY", null, null, false, List.of(9L), 0, 20);
        verify(gridMapper).count("COMMUNITY", null, null, false, List.of(9L));
    }

    private GridService service() {
        return new GridService(gridMapper, taskMapper, dataScopeService, numberGenerator, objectMapper);
    }

    private GridMapper.GridDetailRow grid() {
        return new GridMapper.GridDetailRow(
                9L, 3L, "GRD001", "第一网格", "GRID", "测试路",
                null, null, null, "ENABLED", 2
        );
    }

    private GridMapper.GridDetailRow community() {
        return new GridMapper.GridDetailRow(
                3L, null, "COM001", "第一社区", "COMMUNITY", "测试路",
                null, null, null, "ENABLED", 4
        );
    }

    private AuthenticatedUser admin() {
        return new AuthenticatedUser(
                1L, "admin", "", "系统管理员", true,
                Set.of("SYSTEM_ADMIN"), Set.of("grid:assign")
        );
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(
                5L, "staff", "", "社区工作人员", true,
                Set.of("COMMUNITY_STAFF"), Set.of("grid:assign")
        );
    }
}
