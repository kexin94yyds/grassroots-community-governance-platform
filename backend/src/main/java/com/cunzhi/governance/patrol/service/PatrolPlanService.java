package com.cunzhi.governance.patrol.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.patrol.dto.PatrolPlanCancelRequest;
import com.cunzhi.governance.patrol.dto.PatrolPlanCreateRequest;
import com.cunzhi.governance.patrol.dto.PatrolPlanView;
import com.cunzhi.governance.patrol.mapper.PatrolPlanMapper;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.security.PermissionCodes;
import com.cunzhi.governance.system.security.RoleCodes;
import com.cunzhi.governance.system.service.DataScopeService;
import com.cunzhi.governance.task.domain.TaskStatus;
import com.cunzhi.governance.task.mapper.TaskFlowMapper;
import com.cunzhi.governance.task.mapper.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PatrolPlanService {

    private final PatrolPlanMapper patrolPlanMapper;
    private final TaskMapper taskMapper;
    private final TaskFlowMapper taskFlowMapper;
    private final DataScopeMapper dataScopeMapper;
    private final DataScopeService dataScopeService;
    private final BusinessNumberGenerator numberGenerator;

    public PatrolPlanService(
            PatrolPlanMapper patrolPlanMapper,
            TaskMapper taskMapper,
            TaskFlowMapper taskFlowMapper,
            DataScopeMapper dataScopeMapper,
            DataScopeService dataScopeService,
            BusinessNumberGenerator numberGenerator
    ) {
        this.patrolPlanMapper = patrolPlanMapper;
        this.taskMapper = taskMapper;
        this.taskFlowMapper = taskFlowMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.dataScopeService = dataScopeService;
        this.numberGenerator = numberGenerator;
    }

    public PageResponse<PatrolPlanView> findScoped(int page, int size) {
        DataScope scope = requireStaffReadScope();
        boolean allAccess = scope.type() == DataScopeType.ALL;
        List<Long> gridIds = new ArrayList<>(scope.gridIds());
        return new PageResponse<>(patrolPlanMapper.findPageScoped(
                allAccess, gridIds, (page - 1) * size, size
        ).stream().map(this::toView).toList(), patrolPlanMapper.countScoped(allAccess, gridIds), page, size);
    }

    public List<PatrolPlanView> findMine() {
        AuthenticatedUser user = dataScopeService.currentUser();
        if (!user.roles().contains(RoleCodes.GRID_WORKER)
                || !user.permissions().contains(PermissionCodes.PATROL_READ)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅网格员可查看本人巡查计划");
        }
        return patrolPlanMapper.findByAssigneeUserId(user.id()).stream().map(this::toView).toList();
    }

    @Transactional
    public PatrolPlanView create(PatrolPlanCreateRequest request) {
        AuthenticatedUser operator = requireCommunityPlanWriter();
        long gridId = IdParser.parse(request.gridId(), "网格ID");
        long assigneeUserId = IdParser.parse(request.assigneeUserId(), "网格员ID");
        DataScope scope = dataScopeService.scopeForRole(RoleCodes.COMMUNITY_STAFF);
        if (!scope.allows(gridId) || dataScopeMapper.lockEnabledGrid(gridId) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "巡查计划必须归属当前社区的有效网格");
        }
        if (dataScopeMapper.countActiveGridWorkerAssignment(assigneeUserId, gridId) != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "执行人不是该网格的有效网格员");
        }
        LocalDateTime scheduledAt = request.scheduledAt();
        if (scheduledAt == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "巡查计划时间不能为空");
        }
        if (request.dueAt() != null && request.dueAt().isBefore(scheduledAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "巡查截止时间不能早于计划时间");
        }
        String planNo = numberGenerator.next("PAT");
        ensureUpdated(patrolPlanMapper.insert(
                planNo, gridId, request.title().trim(), request.inspectionContent().trim(), scheduledAt,
                request.dueAt(), assigneeUserId, operator.id()
        ));
        Long planId = patrolPlanMapper.findIdByPlanNo(planNo);
        if (planId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建巡查计划后未能读取计划");
        }
        String taskNo = numberGenerator.next("TSK");
        String priority = request.priority() == null || request.priority().isBlank() ? "MEDIUM" : request.priority().trim();
        if (!List.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(priority)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知巡查优先级");
        }
        ensureUpdated(taskMapper.insertPatrolTask(
                taskNo, planId, gridId, request.title().trim(), request.inspectionContent().trim(), priority,
                operator.id(), assigneeUserId, request.dueAt()
        ));
        long taskId = taskMapper.findIdByTaskNo(taskNo);
        ensureUpdated(taskFlowMapper.insert(taskId, "ASSIGN", null, TaskStatus.PENDING_ACCEPT.name(),
                operator.id(), "创建并派发巡查计划"));
        return toView(requirePlan(planId));
    }

    @Transactional
    public PatrolPlanView cancel(String idValue, PatrolPlanCancelRequest request) {
        AuthenticatedUser operator = requireCommunityPlanWriter();
        long planId = IdParser.parse(idValue, "巡查计划ID");
        PatrolPlanMapper.PlanLockRow plan = requireLockedPlan(planId);
        DataScope scope = dataScopeService.scopeForRole(RoleCodes.COMMUNITY_STAFF);
        if (!scope.allows(plan.gridId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权取消该巡查计划");
        }
        if (!"ACTIVE".equals(plan.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "巡查计划已终结");
        }
        TaskMapper.TaskRow task = taskMapper.findByPatrolPlanIdForUpdate(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "巡查计划缺少关联任务"));
        if (!TaskStatus.PENDING_ACCEPT.name().equals(task.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已接单的巡查计划不能取消");
        }
        ensureUpdated(taskMapper.transition(
                task.id(), task.status(), TaskStatus.CANCELLED.name(), task.version(), null, null
        ));
        ensureUpdated(patrolPlanMapper.cancel(planId, request.version()));
        ensureUpdated(taskFlowMapper.insert(task.id(), "CANCEL", task.status(), TaskStatus.CANCELLED.name(),
                operator.id(), request.reason().trim()));
        return toView(requirePlan(planId));
    }

    private DataScope requireStaffReadScope() {
        AuthenticatedUser user = dataScopeService.currentUser();
        if (user.roles().contains(RoleCodes.SYSTEM_ADMIN)) {
            return DataScope.all();
        }
        if (!user.roles().contains(RoleCodes.COMMUNITY_STAFF)
                || !user.permissions().contains(PermissionCodes.PATROL_READ)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看巡查计划");
        }
        return dataScopeService.scopeForRole(RoleCodes.COMMUNITY_STAFF);
    }

    private AuthenticatedUser requireCommunityPlanWriter() {
        AuthenticatedUser user = dataScopeService.currentUser();
        if (!user.roles().contains(RoleCodes.COMMUNITY_STAFF)
                || !user.permissions().contains(PermissionCodes.PATROL_PLAN_WRITE)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅社区工作人员可维护巡查计划");
        }
        return user;
    }

    private PatrolPlanMapper.PlanRow requirePlan(long id) {
        PatrolPlanMapper.PlanRow row = patrolPlanMapper.findById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "巡查计划不存在");
        }
        return row;
    }

    private PatrolPlanMapper.PlanLockRow requireLockedPlan(long id) {
        PatrolPlanMapper.PlanLockRow row = patrolPlanMapper.findByIdForUpdate(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "巡查计划不存在");
        }
        return row;
    }

    private void ensureUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private PatrolPlanView toView(PatrolPlanMapper.PlanRow row) {
        return new PatrolPlanView(
                row.id().toString(), row.planNo(), row.gridId().toString(), row.gridName(), row.title(),
                row.inspectionContent(), row.scheduledAt(), row.dueAt(), row.assigneeUserId().toString(),
                row.assigneeName(), row.status(), row.taskId() == null ? null : row.taskId().toString(),
                row.taskNo(), row.taskStatus(), row.taskVersion(), row.createdBy().toString(), row.createdByName(),
                row.createdAt(), row.version()
        );
    }
}
