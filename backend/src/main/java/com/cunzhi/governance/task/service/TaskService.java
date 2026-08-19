package com.cunzhi.governance.task.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.event.domain.EventStatus;
import com.cunzhi.governance.event.mapper.EventFlowMapper;
import com.cunzhi.governance.event.mapper.EventMapper;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.security.PermissionCodes;
import com.cunzhi.governance.system.security.RoleCodes;
import com.cunzhi.governance.system.service.DataScopeService;
import com.cunzhi.governance.task.domain.TaskStatus;
import com.cunzhi.governance.task.dto.TaskActionRequest;
import com.cunzhi.governance.task.dto.TaskCreateRequest;
import com.cunzhi.governance.task.dto.TaskFlowView;
import com.cunzhi.governance.task.dto.TaskSummary;
import com.cunzhi.governance.task.mapper.TaskFlowMapper;
import com.cunzhi.governance.task.mapper.TaskAttachmentMapper;
import com.cunzhi.governance.task.mapper.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

@Service
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskFlowMapper taskFlowMapper;
    private final TaskAttachmentMapper taskAttachmentMapper;
    private final EventMapper eventMapper;
    private final EventFlowMapper eventFlowMapper;
    private final DataScopeMapper dataScopeMapper;
    private final DataScopeService dataScopeService;
    private final BusinessNumberGenerator numberGenerator;

    public TaskService(
            TaskMapper taskMapper,
            TaskFlowMapper taskFlowMapper,
            TaskAttachmentMapper taskAttachmentMapper,
            EventMapper eventMapper,
            EventFlowMapper eventFlowMapper,
            DataScopeMapper dataScopeMapper,
            DataScopeService dataScopeService,
            BusinessNumberGenerator numberGenerator
    ) {
        this.taskMapper = taskMapper;
        this.taskFlowMapper = taskFlowMapper;
        this.taskAttachmentMapper = taskAttachmentMapper;
        this.eventMapper = eventMapper;
        this.eventFlowMapper = eventFlowMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.dataScopeService = dataScopeService;
        this.numberGenerator = numberGenerator;
    }

    public TaskSummary findById(String id) {
        TaskMapper.TaskRow row = requireTask(IdParser.parse(id, "任务ID"));
        requireTaskReadAccess(row);
        return toSummary(row);
    }

    public PageResponse<TaskSummary> findPage(String keyword, String status, int page, int size) {
        DataScope scope = dataScopeService.currentScope();
        boolean allAccess = scope.type() == DataScopeType.ALL;
        List<Long> gridIds = new ArrayList<>(scope.gridIds());
        String normalizedKeyword = normalizeText(keyword);
        String normalizedStatus = normalizeStatus(status);
        Long assigneeUserId = currentReadAssigneeUserId();

        List<TaskSummary> items = taskMapper.findPage(
                        normalizedKeyword, normalizedStatus, allAccess, gridIds,
                        assigneeUserId, (page - 1) * size, size
                ).stream()
                .map(this::toSummary)
                .toList();
        long total = taskMapper.count(normalizedKeyword, normalizedStatus, allAccess, gridIds, assigneeUserId);
        return new PageResponse<>(items, total, page, size);
    }

    @Transactional
    public TaskSummary create(TaskCreateRequest request) {
        long gridId = IdParser.parse(request.gridId(), "网格ID");
        long assigneeId = IdParser.parse(request.assigneeUserId(), "执行人ID");
        dataScopeService.requireGridAccess(gridId);
        if (dataScopeMapper.lockEnabledGrid(gridId) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "任务必须归属有效网格");
        }
        if (dataScopeMapper.countActiveGridWorkerAssignment(assigneeId, gridId) != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "执行人不是该网格的有效网格员");
        }
        AuthenticatedUser operator = dataScopeService.currentUser();
        String taskNo = numberGenerator.next("TSK");
        taskMapper.insertIndependentTask(
                taskNo, gridId, request.taskType(), request.title().trim(),
                normalizeText(request.description()), request.priority(), operator.id(),
                assigneeId, request.dueAt()
        );
        long taskId = taskMapper.findIdByTaskNo(taskNo);
        taskFlowMapper.insert(
                taskId, "ASSIGN", null, TaskStatus.PENDING_ACCEPT.name(),
                operator.id(), "创建并派发独立任务"
        );
        return toSummary(requireTask(taskId));
    }

    public List<TaskFlowView> findFlows(String id) {
        long taskId = IdParser.parse(id, "任务ID");
        TaskMapper.TaskRow task = requireTask(taskId);
        requireTaskReadAccess(task);
        return taskFlowMapper.findByTaskId(taskId).stream()
                .map(row -> new TaskFlowView(
                        row.id().toString(),
                        row.taskId().toString(),
                        row.action(),
                        row.fromStatus(),
                        row.toStatus(),
                        row.operatorUserId() == null ? null : row.operatorUserId().toString(),
                        row.operatorName(),
                        row.remark(),
                        row.createdAt()
                ))
                .toList();
    }

    @Transactional
    public TaskSummary accept(String id, TaskActionRequest request) {
        return transition(id, request, TaskStatus.PROCESSING, "ACCEPT", true, false);
    }

    @Transactional
    public TaskSummary submitReview(String id, TaskActionRequest request) {
        if (request.handlingResult() == null || request.handlingResult().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "提交复核必须填写处置结果");
        }
        return transition(id, request, TaskStatus.PENDING_REVIEW, "SUBMIT_REVIEW", true, false);
    }

    @Transactional
    public TaskSummary review(String id, TaskActionRequest request) {
        if (request.approved() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "复核必须明确是否通过");
        }
        if (!request.approved() && normalizeText(request.remark()) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "复核退回必须填写原因");
        }
        return transition(
                id,
                request,
                request.approved() ? TaskStatus.COMPLETED : TaskStatus.PROCESSING,
                request.approved() ? "APPROVE" : "RETURN",
                false,
                true
        );
    }

    @Transactional
    public TaskSummary cancel(String id, TaskActionRequest request) {
        long taskId = IdParser.parse(id, "任务ID");
        TaskMapper.TaskRow task = requireTask(taskId);
        dataScopeService.requireGridAccess(task.gridId());
        if (task.sourceEventId() != null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "事件派生任务不能单独取消，请通过事件工作流处理"
            );
        }
        if (taskMapper.countPatrolTaskById(taskId) > 0
                && !dataScopeService.currentUser().permissions().contains(PermissionCodes.PATROL_PLAN_WRITE)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "巡查计划只能由社区工作人员通过计划入口取消");
        }
        String reason = normalizeText(request.reason());
        if (reason == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "取消任务必须填写原因");
        }
        TaskStatus from = TaskStatus.valueOf(task.status());
        from.requireTransitionTo(TaskStatus.CANCELLED);
        ensureUpdated(taskMapper.transition(
                taskId, from.name(), TaskStatus.CANCELLED.name(), request.version(), null, null
        ));
        taskFlowMapper.insert(
                taskId, "CANCEL", from.name(), TaskStatus.CANCELLED.name(),
                dataScopeService.currentUser().id(), reason
        );
        taskMapper.cancelPatrolPlanByTaskId(taskId);
        return toSummary(requireTask(taskId));
    }

    private TaskSummary transition(
            String id,
            TaskActionRequest request,
            TaskStatus target,
            String action,
            boolean assigneeOnly,
            boolean reviewer
    ) {
        long taskId = IdParser.parse(id, "任务ID");
        TaskMapper.TaskRow task = target == TaskStatus.PENDING_REVIEW
                ? requireTaskForUpdate(taskId)
                : requireTask(taskId);
        dataScopeService.requireGridAccess(task.gridId());
        AuthenticatedUser operator = dataScopeService.currentUser();
        if (assigneeOnly && !task.assigneeUserId().equals(operator.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有任务执行人可以执行该动作");
        }
        if (reviewer && (task.assigneeUserId().equals(operator.id())
                || task.dispatcherUserId().equals(operator.id()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "复核人不能是执行人或派发人");
        }
        TaskStatus from = TaskStatus.valueOf(task.status());
        from.requireTransitionTo(target);
        if (target == TaskStatus.PENDING_REVIEW) {
            validateTaskAttachmentIds(taskId, request.attachmentIds());
        }
        if (reviewer && task.sourceEventId() != null && request.eventVersion() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "事件派生任务复核必须携带事件版本");
        }
        String handlingResult = target == TaskStatus.PENDING_REVIEW
                ? normalizeText(request.handlingResult())
                : null;
        String reviewRemark = reviewer ? normalizeText(request.remark()) : null;
        ensureUpdated(taskMapper.transition(
                taskId, from.name(), target.name(), request.version(),
                handlingResult, reviewRemark
        ));

        EventMapper.EventRow event = null;
        EventStatus eventTarget = null;
        if (task.sourceEventId() != null) {
            event = eventMapper.findById(task.sourceEventId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "关联事件不存在"));
            EventStatus eventFrom = EventStatus.valueOf(event.status());
            eventTarget = switch (target) {
                case PROCESSING -> EventStatus.PROCESSING;
                case PENDING_REVIEW -> EventStatus.PENDING_REVIEW;
                case COMPLETED -> EventStatus.CLOSED;
                case PENDING_ACCEPT, CANCELLED ->
                        throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
            };
            eventFrom.requireTransitionTo(eventTarget);
            int expectedEventVersion = request.eventVersion() == null
                    ? event.version()
                    : request.eventVersion();
            ensureUpdated(eventMapper.transition(
                    event.id(), eventFrom.name(), eventTarget.name(), expectedEventVersion,
                    null, target == TaskStatus.COMPLETED ? task.handlingResult() : null
            ));
            eventFlowMapper.insert(
                    event.id(), taskId, eventAction(action), eventFrom.name(), eventTarget.name(),
                    operator.id(), reviewRemark == null ? request.remark() : reviewRemark
            );
        }
        taskFlowMapper.insert(
                taskId, action, from.name(), target.name(), operator.id(),
                reviewRemark == null ? request.remark() : reviewRemark
        );
        if (target == TaskStatus.COMPLETED) {
            taskMapper.completePatrolPlanByTaskId(taskId);
        }
        return toSummary(requireTask(taskId));
    }

    private String eventAction(String taskAction) {
        return switch (taskAction) {
            case "ACCEPT" -> "START";
            default -> taskAction;
        };
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeText(status);
        if (normalized == null) {
            return null;
        }
        try {
            return TaskStatus.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知任务状态");
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void validateTaskAttachmentIds(long taskId, List<String> attachmentIds) {
        if (attachmentIds.isEmpty()) {
            return;
        }
        List<Long> ids = attachmentIds.stream().map(id -> IdParser.parse(id, "附件ID")).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件ID不能重复");
        }
        if (taskAttachmentMapper.countActiveByTaskAndIds(taskId, ids) != ids.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件不存在、已删除或不属于当前任务");
        }
    }

    private TaskMapper.TaskRow requireTask(long id) {
        return taskMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在"));
    }

    private TaskMapper.TaskRow requireTaskForUpdate(long id) {
        return taskMapper.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在"));
    }

    private void requireTaskReadAccess(TaskMapper.TaskRow task) {
        dataScopeService.requireGridAccess(task.gridId());
        Long assigneeUserId = currentReadAssigneeUserId();
        if (assigneeUserId != null && !task.assigneeUserId().equals(assigneeUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "网格员只能查看本人任务");
        }
    }

    private Long currentReadAssigneeUserId() {
        AuthenticatedUser user = dataScopeService.currentUser();
        boolean elevated = user.roles().contains(RoleCodes.SYSTEM_ADMIN)
                || user.roles().contains(RoleCodes.COMMUNITY_STAFF);
        return !elevated && user.roles().contains(RoleCodes.GRID_WORKER) ? user.id() : null;
    }

    private void ensureUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private TaskSummary toSummary(TaskMapper.TaskRow row) {
        return new TaskSummary(
                row.id().toString(), row.taskNo(),
                row.sourceEventId() == null ? null : row.sourceEventId().toString(),
                row.sourceEventNo(),
                row.gridId().toString(), row.gridName(),
                row.taskType(), row.title(), row.description(),
                row.priority(), row.status(),
                row.dispatcherUserId().toString(), row.dispatcherName(),
                row.assigneeUserId().toString(), row.assigneeName(),
                row.dueAt(), row.assignedAt(), row.handlingResult(), row.reviewRemark(), row.version()
        );
    }
}
