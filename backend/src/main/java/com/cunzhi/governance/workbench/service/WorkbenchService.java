package com.cunzhi.governance.workbench.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.resident.mapper.ResidentMapper;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.PermissionCodes;
import com.cunzhi.governance.system.security.RoleCodes;
import com.cunzhi.governance.system.service.DataScopeService;
import com.cunzhi.governance.workbench.dto.SystemHealthView;
import com.cunzhi.governance.workbench.dto.SystemOperationView;
import com.cunzhi.governance.workbench.dto.WorkbenchItem;
import com.cunzhi.governance.workbench.dto.WorkbenchSummary;
import com.cunzhi.governance.workbench.mapper.WorkbenchMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkbenchService {

    private final WorkbenchMapper mapper;
    private final DataScopeService dataScopeService;
    private final DataScopeMapper dataScopeMapper;
    private final ResidentMapper residentMapper;

    public WorkbenchService(
            WorkbenchMapper mapper,
            DataScopeService dataScopeService,
            DataScopeMapper dataScopeMapper,
            ResidentMapper residentMapper
    ) {
        this.mapper = mapper;
        this.dataScopeService = dataScopeService;
        this.dataScopeMapper = dataScopeMapper;
        this.residentMapper = residentMapper;
    }

    public WorkbenchSummary adminSummary() {
        requireRolePermission(RoleCodes.SYSTEM_ADMIN, PermissionCodes.WORKBENCH_ADMIN_READ);
        WorkbenchMapper.AdminMetricRow row = mapper.adminMetrics();
        return new WorkbenchSummary(RoleCodes.SYSTEM_ADMIN, "全区治理与系统运行", Map.of(
                "totalUsers", row.totalUsers(),
                "pendingRegistrations", row.pendingRegistrations(),
                "publishedAnnouncements", row.publishedAnnouncements(),
                "openApplications", row.openApplications(),
                "activePatrolPlans", row.activePatrolPlans(),
                "openEvents", row.openEvents()
        ), toItems("REGISTRATION", mapper.findAdminFocus()), toItems("ANNOUNCEMENT", mapper.findAdminRecent()));
    }

    public WorkbenchSummary communitySummary() {
        requireRolePermission(RoleCodes.COMMUNITY_STAFF, PermissionCodes.WORKBENCH_COMMUNITY_READ);
        DataScope scope = dataScopeService.scopeForRole(RoleCodes.COMMUNITY_STAFF);
        if (scope.gridIds().isEmpty()) {
            return emptySummary(RoleCodes.COMMUNITY_STAFF, "未分配社区");
        }
        List<Long> gridIds = List.copyOf(scope.gridIds());
        WorkbenchMapper.CommunityMetricRow row = mapper.communityMetrics(gridIds);
        return new WorkbenchSummary(RoleCodes.COMMUNITY_STAFF, "所属社区子网格", Map.of(
                "reportedEvents", row.reportedEvents(),
                "pendingReviews", row.pendingReviews(),
                "openApplications", row.openApplications(),
                "activePatrolPlans", row.activePatrolPlans(),
                "activeResidents", row.activeResidents()
        ), toItems("SERVICE_APPLICATION", mapper.findCommunityFocus(gridIds)),
                toItems("EVENT", mapper.findCommunityRecent(gridIds)));
    }

    public WorkbenchSummary gridSummary() {
        AuthenticatedUser user = requireRolePermission(RoleCodes.GRID_WORKER, PermissionCodes.WORKBENCH_GRID_READ);
        DataScope scope = dataScopeService.scopeForRole(RoleCodes.GRID_WORKER);
        if (scope.gridIds().isEmpty()) {
            return emptySummary(RoleCodes.GRID_WORKER, "未分配责任网格");
        }
        WorkbenchMapper.GridMetricRow row = mapper.gridMetrics(user.id(), List.copyOf(scope.gridIds()));
        return new WorkbenchSummary(RoleCodes.GRID_WORKER, "本人责任网格与执行任务", Map.of(
                "pendingAccept", row.pendingAccept(),
                "processing", row.processing(),
                "pendingReview", row.pendingReview(),
                "overdue", row.overdue(),
                "activePatrolPlans", row.activePatrolPlans(),
                "reportsLast7Days", row.reportsLast7Days()
        ), toItems("TASK", mapper.findGridFocus(user.id())), toItems("TASK", mapper.findGridRecent(user.id())));
    }

    public WorkbenchSummary residentSummary() {
        AuthenticatedUser user = requireRolePermission(RoleCodes.RESIDENT, PermissionCodes.WORKBENCH_RESIDENT_READ);
        ResidentMapper.ResidentRow resident = residentMapper.findByUserId(user.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "当前账号未绑定有效居民档案"));
        Long communityId = dataScopeMapper.findParentCommunityId(resident.gridId());
        WorkbenchMapper.ResidentMetricRow row = mapper.residentMetrics(user.id(), communityId);
        return new WorkbenchSummary(RoleCodes.RESIDENT, "本人事项与社区服务", Map.of(
                "openEvents", row.openEvents(),
                "openApplications", row.openApplications(),
                "pendingRatings", row.pendingRatings(),
                "visibleAnnouncements", row.visibleAnnouncements()
        ), toItems("SERVICE_APPLICATION", mapper.findResidentFocus(user.id())),
                toItems("ANNOUNCEMENT", mapper.findResidentRecent(communityId)));
    }

    public PageResponse<SystemOperationView> operations(String module, String keyword, int page, int size) {
        requireRolePermission(RoleCodes.SYSTEM_ADMIN, PermissionCodes.SYSTEM_AUDIT_READ);
        String normalizedModule = normalizeModule(module);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<SystemOperationView> items = mapper.findOperations(
                normalizedModule, normalizedKeyword, (page - 1) * size, size
        ).stream().map(row -> new SystemOperationView(
                row.id(), row.createdAt(), row.module(), row.moduleLabel(), row.action(), row.actionLabel(),
                row.operatorName(), row.objectLabel(), row.scopeLabel(), row.result(), row.resultLabel()
        )).toList();
        return new PageResponse<>(items, mapper.countOperations(normalizedModule, normalizedKeyword), page, size);
    }

    public SystemHealthView health() {
        requireRolePermission(RoleCodes.SYSTEM_ADMIN, PermissionCodes.SYSTEM_HEALTH_READ);
        WorkbenchMapper.HealthCountRow counts = mapper.healthCounts();
        WorkbenchMapper.ConsistencyRow consistency = mapper.consistencyCounts();
        boolean consistent = consistency.patrolWithoutTask() == 0
                && consistency.activePatrolWithTerminalTask() == 0
                && consistency.cancelledPatrolWithNonCancelledTask() == 0
                && consistency.completedPatrolWithNonCompletedTask() == 0
                && consistency.incompleteServiceTimestamp() == 0;
        return new SystemHealthView(mapper.databaseTime(), mapper.latestFlywayVersion(), counts.enabledUserCount(),
                consistent ? "HEALTHY" : "FAILED", Map.of(
                "users", counts.userCount(), "roles", counts.roleCount(), "menus", counts.menuCount(),
                "events", counts.eventCount(), "tasks", counts.taskCount(), "announcements", counts.announcementCount(),
                "serviceApplications", counts.serviceApplicationCount(), "patrolPlans", counts.patrolPlanCount()
        ), Map.of(
                "patrolWithoutTask", consistency.patrolWithoutTask(),
                "activePatrolWithTerminalTask", consistency.activePatrolWithTerminalTask(),
                "cancelledPatrolWithNonCancelledTask", consistency.cancelledPatrolWithNonCancelledTask(),
                "completedPatrolWithNonCompletedTask", consistency.completedPatrolWithNonCompletedTask(),
                "incompleteServiceTimestamp", consistency.incompleteServiceTimestamp()
        ));
    }

    private AuthenticatedUser requireRolePermission(String roleCode, String permission) {
        AuthenticatedUser user = dataScopeService.currentUser();
        if (!user.roles().contains(roleCode) || !user.permissions().contains(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该角色工作台");
        }
        return user;
    }

    private WorkbenchSummary emptySummary(String role, String scopeLabel) {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("pending", 0L);
        metrics.put("processing", 0L);
        metrics.put("completed", 0L);
        return new WorkbenchSummary(role, scopeLabel, metrics, List.of(), List.of());
    }

    private String normalizeModule(String module) {
        if (module == null || module.isBlank()) {
            return null;
        }
        String normalized = module.trim();
        if (!List.of("EVENT", "TASK", "RESIDENT_SENSITIVE", "ANNOUNCEMENT", "SERVICE_APPLICATION").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知审计模块");
        }
        return normalized;
    }

    private List<WorkbenchItem> toItems(String type, List<WorkbenchMapper.ItemRow> rows) {
        return rows.stream().map(row -> new WorkbenchItem(
                type, row.id(), row.title(), row.status(), row.route(), row.occurredAt()
        )).toList();
    }
}
