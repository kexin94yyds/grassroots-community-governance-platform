package com.cunzhi.governance.task.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.event.mapper.EventFlowMapper;
import com.cunzhi.governance.event.mapper.EventMapper;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.service.DataScopeService;
import com.cunzhi.governance.task.dto.TaskCreateRequest;
import com.cunzhi.governance.task.dto.TaskActionRequest;
import com.cunzhi.governance.task.dto.TaskFlowView;
import com.cunzhi.governance.task.dto.TaskSummary;
import com.cunzhi.governance.task.mapper.TaskFlowMapper;
import com.cunzhi.governance.task.mapper.TaskAttachmentMapper;
import com.cunzhi.governance.task.mapper.TaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TaskFlowMapper taskFlowMapper;
    @Mock
    private TaskAttachmentMapper taskAttachmentMapper;
    @Mock
    private EventMapper eventMapper;
    @Mock
    private EventFlowMapper eventFlowMapper;
    @Mock
    private DataScopeMapper dataScopeMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private BusinessNumberGenerator numberGenerator;

    @Test
    void createsIndependentTaskAndInitialFlowInOneServiceOperation() {
        LocalDateTime dueAt = LocalDateTime.now().plusDays(1);
        when(dataScopeMapper.lockEnabledGrid(7)).thenReturn(7L);
        when(dataScopeMapper.countActiveGridWorkerAssignment(12, 7)).thenReturn(1);
        when(dataScopeService.currentUser()).thenReturn(user(5));
        when(numberGenerator.next("TSK")).thenReturn("TSK0001");
        when(taskMapper.findIdByTaskNo("TSK0001")).thenReturn(42L);
        when(taskMapper.findById(42)).thenReturn(Optional.of(taskRow(42)));

        TaskSummary created = service().create(new TaskCreateRequest(
                "7", "ROUTINE_INSPECTION", "消防巡查", "检查消防通道",
                "HIGH", "12", dueAt
        ));

        assertThat(created.id()).isEqualTo("42");
        assertThat(created.taskType()).isEqualTo("ROUTINE_INSPECTION");
        verify(taskMapper).insertIndependentTask(
                "TSK0001", 7, "ROUTINE_INSPECTION", "消防巡查", "检查消防通道",
                "HIGH", 5, 12, dueAt
        );
        verify(taskFlowMapper).insert(
                42, "ASSIGN", null, "PENDING_ACCEPT", 5, "创建并派发独立任务"
        );
    }

    @Test
    void rejectsAssigneeOutsideTheGrid() {
        when(dataScopeMapper.lockEnabledGrid(7)).thenReturn(7L);
        when(dataScopeMapper.countActiveGridWorkerAssignment(12, 7)).thenReturn(0);

        assertThatThrownBy(() -> service().create(new TaskCreateRequest(
                "7", "OTHER", "临时任务", null, "MEDIUM", "12", null
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(taskMapper, never()).insertIndependentTask(
                any(), anyLong(), any(), any(), any(), any(), anyLong(), anyLong(), any()
        );
    }

    @Test
    void returnsFlowIdsAsStringsAfterScopeCheck() {
        LocalDateTime createdAt = LocalDateTime.now();
        when(taskMapper.findById(42)).thenReturn(Optional.of(taskRow(42)));
        when(dataScopeService.currentUser()).thenReturn(user(5));
        when(taskFlowMapper.findByTaskId(42)).thenReturn(List.of(
                new TaskFlowMapper.TaskFlowRow(
                        8L, 42L, "ASSIGN", null, "PENDING_ACCEPT",
                        5L, "派发人", "创建并派发独立任务", createdAt
                )
        ));

        List<TaskFlowView> flows = service().findFlows("42");

        assertThat(flows).singleElement().satisfies(flow -> {
            assertThat(flow.id()).isEqualTo("8");
            assertThat(flow.taskId()).isEqualTo("42");
            assertThat(flow.operatorUserId()).isEqualTo("5");
        });
        verify(dataScopeService).requireGridAccess(7);
    }

    @Test
    void gridWorkerCannotReadAnotherAssigneesTaskDetailsOrFlows() {
        when(taskMapper.findById(42)).thenReturn(Optional.of(taskRow(42)));
        when(dataScopeService.currentUser()).thenReturn(gridWorker(99));

        assertThatThrownBy(() -> service().findById("42"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service().findFlows("42"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(taskFlowMapper, never()).findByTaskId(42L);
    }

    @Test
    void gridWorkerPageUsesCurrentUserAsAssigneeFilter() {
        when(dataScopeService.currentScope()).thenReturn(new DataScope(DataScopeType.GRID, Set.of(7L)));
        when(dataScopeService.currentUser()).thenReturn(gridWorker(12));
        when(taskMapper.findPage(null, null, false, List.of(7L), 12L, 0, 20)).thenReturn(List.of());
        when(taskMapper.count(null, null, false, List.of(7L), 12L)).thenReturn(0L);

        var page = service().findPage(null, null, 1, 20);

        assertThat(page.items()).isEmpty();
        verify(taskMapper).findPage(null, null, false, List.of(7L), 12L, 0, 20);
        verify(taskMapper).count(null, null, false, List.of(7L), 12L);
    }

    @Test
    void acceptsValidatedAttachmentIdsAsReservedSubmitReviewField() {
        TaskMapper.TaskRow processing = new TaskMapper.TaskRow(
                42L, "TSK0001", null, null, 7L, "第一网格",
                "OTHER", "临时任务", null,
                "MEDIUM", "PROCESSING", 5L, "派发人", 12L, "执行人", null,
                LocalDateTime.now(), null, null, 0
        );
        TaskMapper.TaskRow pendingReview = new TaskMapper.TaskRow(
                42L, "TSK0001", null, null, 7L, "第一网格",
                "OTHER", "临时任务", null,
                "MEDIUM", "PENDING_REVIEW", 5L, "派发人", 12L, "执行人", null,
                processing.assignedAt(), "已处理", null, 1
        );
        when(taskMapper.findByIdForUpdate(42)).thenReturn(Optional.of(processing));
        when(taskMapper.findById(42)).thenReturn(Optional.of(pendingReview));
        when(dataScopeService.currentUser()).thenReturn(user(12));
        when(taskMapper.transition(
                42, "PROCESSING", "PENDING_REVIEW", 0, "已处理", null
        )).thenReturn(1);
        when(taskAttachmentMapper.countActiveByTaskAndIds(42, List.of(8L, 9L))).thenReturn(2);

        TaskSummary result = service().submitReview(
                "42",
                new TaskActionRequest(
                        0, null, null, "已处理", List.of("8", "9"), "提交复核", null
                )
        );

        assertThat(result.status()).isEqualTo("PENDING_REVIEW");
        assertThat(result.gridName()).isEqualTo("第一网格");
        assertThat(result.assigneeName()).isEqualTo("执行人");
        verify(taskFlowMapper).insert(
                42, "SUBMIT_REVIEW", "PROCESSING", "PENDING_REVIEW", 12, "提交复核"
        );
    }

    @Test
    void rejectsSubmitReviewWhenAttachmentBelongsToAnotherTaskOrWasDeleted() {
        TaskMapper.TaskRow processing = new TaskMapper.TaskRow(
                42L, "TSK0001", null, null, 7L, "第一网格",
                "OTHER", "临时任务", null,
                "MEDIUM", "PROCESSING", 5L, "派发人", 12L, "执行人", null,
                LocalDateTime.now(), null, null, 0
        );
        when(taskMapper.findByIdForUpdate(42)).thenReturn(Optional.of(processing));
        when(dataScopeService.currentUser()).thenReturn(user(12));
        // The requested list has two IDs, but only one remains active on this task.
        when(taskAttachmentMapper.countActiveByTaskAndIds(42, List.of(8L, 9L))).thenReturn(1);

        assertThatThrownBy(() -> service().submitReview(
                "42",
                new TaskActionRequest(0, null, null, "已处理", List.of("8", "9"), "提交复核", null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(taskMapper, never()).transition(
                42, "PROCESSING", "PENDING_REVIEW", 0, "已处理", null
        );
    }

    @Test
    void doesNotProbeAttachmentIdsBeforeTaskScopeAndAssigneeChecks() {
        TaskMapper.TaskRow processing = new TaskMapper.TaskRow(
                42L, "TSK0001", null, null, 7L, "第一网格",
                "OTHER", "临时任务", null,
                "MEDIUM", "PROCESSING", 5L, "派发人", 12L, "执行人", null,
                LocalDateTime.now(), null, null, 0
        );
        when(taskMapper.findByIdForUpdate(42)).thenReturn(Optional.of(processing));
        when(dataScopeService.currentUser()).thenReturn(user(99));

        assertThatThrownBy(() -> service().submitReview(
                "42", new TaskActionRequest(0, null, null, "已处理", List.of("8"), "提交复核", null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(taskAttachmentMapper, never()).countActiveByTaskAndIds(anyLong(), any());
    }

    @Test
    void doesNotProbeAttachmentIdsWhenTaskIsNoLongerProcessable() {
        TaskMapper.TaskRow completed = new TaskMapper.TaskRow(
                42L, "TSK0001", null, null, 7L, "第一网格",
                "OTHER", "临时任务", null,
                "MEDIUM", "COMPLETED", 5L, "派发人", 12L, "执行人", null,
                LocalDateTime.now(), null, null, 0
        );
        when(taskMapper.findByIdForUpdate(42)).thenReturn(Optional.of(completed));
        when(dataScopeService.currentUser()).thenReturn(user(12));

        assertThatThrownBy(() -> service().submitReview(
                "42", new TaskActionRequest(0, null, null, "已处理", List.of("8"), "提交复核", null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        verify(taskAttachmentMapper, never()).countActiveByTaskAndIds(anyLong(), any());
    }

    private TaskService service() {
        return new TaskService(
                taskMapper, taskFlowMapper, taskAttachmentMapper, eventMapper, eventFlowMapper,
                dataScopeMapper, dataScopeService, numberGenerator
        );
    }

    private AuthenticatedUser user(long id) {
        return new AuthenticatedUser(
                id, "staff", "", "社区工作人员", true,
                Set.of("COMMUNITY_STAFF"), Set.of("task:create")
        );
    }

    private AuthenticatedUser gridWorker(long id) {
        return new AuthenticatedUser(
                id, "worker", "", "网格员", true,
                Set.of("GRID_WORKER"), Set.of("task:read", "task:accept", "task:handle")
        );
    }

    private TaskMapper.TaskRow taskRow(long id) {
        return new TaskMapper.TaskRow(
                id, "TSK0001", null, null, 7L, "第一网格", "ROUTINE_INSPECTION",
                "消防巡查", "检查消防通道", "HIGH", "PENDING_ACCEPT",
                5L, "派发人", 12L, "执行人",
                null, LocalDateTime.now(), null, null, 0
        );
    }
}
