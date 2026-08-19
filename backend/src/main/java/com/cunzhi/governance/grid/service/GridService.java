package com.cunzhi.governance.grid.service;

import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.grid.dto.AreaOption;
import com.cunzhi.governance.grid.dto.GridAssignmentView;
import com.cunzhi.governance.grid.dto.GridAssignmentsRequest;
import com.cunzhi.governance.grid.dto.GridCreateRequest;
import com.cunzhi.governance.grid.dto.GridDetail;
import com.cunzhi.governance.grid.dto.GridStatusRequest;
import com.cunzhi.governance.grid.dto.GridSummary;
import com.cunzhi.governance.grid.dto.GridUpdateRequest;
import com.cunzhi.governance.grid.dto.WorkerOption;
import com.cunzhi.governance.grid.mapper.GridMapper;
import com.cunzhi.governance.task.mapper.TaskMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.security.RoleCodes;
import com.cunzhi.governance.system.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GridService {

    private final GridMapper gridMapper;
    private final TaskMapper taskMapper;
    private final DataScopeService dataScopeService;
    private final BusinessNumberGenerator numberGenerator;
    private final ObjectMapper objectMapper;

    public GridService(
            GridMapper gridMapper,
            TaskMapper taskMapper,
            DataScopeService dataScopeService,
            BusinessNumberGenerator numberGenerator,
            ObjectMapper objectMapper
    ) {
        this.gridMapper = gridMapper;
        this.taskMapper = taskMapper;
        this.dataScopeService = dataScopeService;
        this.numberGenerator = numberGenerator;
        this.objectMapper = objectMapper;
    }

    public GridDetail findById(String id) {
        GridMapper.GridDetailRow row = requireArea(IdParser.parse(id, "区域ID"));
        requireAreaReadAccess(row);
        return toDetail(row);
    }

    @Transactional
    public GridDetail create(GridCreateRequest request) {
        AuthenticatedUser operator = dataScopeService.currentUser();
        String areaType = request.areaType();
        Long communityId = null;
        if ("COMMUNITY".equals(areaType)) {
            if (!operator.roles().contains(RoleCodes.SYSTEM_ADMIN)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只有系统管理员可以创建社区");
            }
        } else {
            if (request.communityId() == null || request.communityId().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "创建网格必须选择所属社区");
            }
            communityId = IdParser.parse(request.communityId(), "社区ID");
            if (gridMapper.countEnabledCommunity(communityId) != 1) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "所属社区不存在或已停用");
            }
            requireCommunityManageAccess(communityId, operator);
        }
        String areaCode = numberGenerator.next("COMMUNITY".equals(areaType) ? "COM" : "GRD");
        gridMapper.insertArea(
                communityId, areaCode, request.areaName().trim(), areaType,
                normalizeText(request.address()), request.centerLongitude(), request.centerLatitude(),
                normalizeJson(request.boundaryGeojson()), operator.id()
        );
        Long areaId = gridMapper.findIdByAreaCode(areaCode);
        if (areaId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建区域后未能读取区域");
        }
        return toDetail(requireArea(areaId));
    }

    @Transactional
    public GridDetail update(String id, GridUpdateRequest request) {
        long areaId = IdParser.parse(id, "区域ID");
        GridMapper.GridDetailRow row = requireArea(areaId);
        requireAreaWriteAccess(row);
        ensureUpdated(gridMapper.updateArea(
                areaId, request.areaName().trim(), normalizeText(request.address()),
                request.centerLongitude(), request.centerLatitude(),
                normalizeJson(request.boundaryGeojson()), request.version()
        ));
        return toDetail(requireArea(areaId));
    }

    @Transactional
    public GridDetail updateStatus(String id, GridStatusRequest request) {
        long areaId = IdParser.parse(id, "区域ID");
        GridMapper.GridDetailRow row = requireArea(areaId);
        requireAreaWriteAccess(row);
        if ("DISABLED".equals(request.status())) {
            if ("COMMUNITY".equals(row.areaType()) && gridMapper.countEnabledChildGrids(areaId) > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "社区仍有启用网格，不能停用");
            }
            if ("GRID".equals(row.areaType())
                    && (gridMapper.countActiveResidents(areaId) > 0
                    || gridMapper.countOpenEvents(areaId) > 0
                    || gridMapper.countOpenTasks(areaId) > 0
                    || gridMapper.countOpenServiceApplications(areaId) > 0)) {
                throw new BusinessException(ErrorCode.CONFLICT, "网格仍有有效居民、未办结事件、进行中任务或服务申请");
            }
        } else if ("GRID".equals(row.areaType())
                && (row.communityId() == null || gridMapper.countEnabledCommunity(row.communityId()) != 1)) {
            throw new BusinessException(ErrorCode.CONFLICT, "所属社区已停用，不能启用网格");
        }
        ensureUpdated(gridMapper.updateStatus(areaId, request.status(), request.version()));
        return toDetail(requireArea(areaId));
    }

    @Transactional
    public GridDetail replaceAssignments(String id, GridAssignmentsRequest request) {
        long areaId = IdParser.parse(id, "区域ID");
        if (gridMapper.lockArea(areaId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "区域不存在");
        }
        GridMapper.GridDetailRow row = requireArea(areaId);
        AuthenticatedUser operator = dataScopeService.currentUser();
        boolean community = "COMMUNITY".equals(row.areaType());
        if (community && !operator.roles().contains(RoleCodes.SYSTEM_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有系统管理员可以分配社区工作人员");
        }
        requireAreaWriteAccess(row);
        String roleCode = community ? RoleCodes.COMMUNITY_STAFF : RoleCodes.GRID_WORKER;
        String assignmentType = roleCode;
        String assigneeLabel = community ? "社区工作人员" : "网格员";

        List<ResolvedAssignment> assignments = request.assignments().stream()
                .map(item -> new ResolvedAssignment(
                        IdParser.parse(item.userId(), assigneeLabel + "ID"),
                        item.isPrimary()
                ))
                .toList();
        Set<Long> uniqueUsers = new HashSet<>();
        long primaryCount = assignments.stream().filter(ResolvedAssignment::primary).count();
        if (primaryCount != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "必须且只能指定一个主负责人");
        }
        for (ResolvedAssignment assignment : assignments) {
            if (!uniqueUsers.add(assignment.userId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, assigneeLabel + "不能重复");
            }
            if (gridMapper.countEnabledAssignee(assignment.userId(), roleCode) != 1) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "包含无效或已停用的" + assigneeLabel);
            }
        }

        List<GridMapper.AssignmentRow> currentAssignments =
                gridMapper.findActiveAssignmentsForUpdate(areaId, assignmentType);
        if (!community) {
            Set<Long> requestedUsers = assignments.stream()
                    .map(ResolvedAssignment::userId)
                    .collect(java.util.stream.Collectors.toSet());
            boolean hasOrphanedTask = currentAssignments.stream()
                    .map(GridMapper.AssignmentRow::userId)
                    .filter(userId -> !requestedUsers.contains(userId))
                    .anyMatch(userId -> taskMapper.countUnfinishedByGridAndAssignee(areaId, userId) > 0);
            if (hasOrphanedTask) {
                throw new BusinessException(ErrorCode.CONFLICT, "被移除网格员仍有未终止任务，不能撤销责任范围");
            }
        } else {
            Set<Long> requestedUsers = assignments.stream()
                    .map(ResolvedAssignment::userId)
                    .collect(java.util.stream.Collectors.toSet());
            boolean hasOrphanedService = currentAssignments.stream()
                    .map(GridMapper.AssignmentRow::userId)
                    .filter(userId -> !requestedUsers.contains(userId))
                    .anyMatch(userId -> gridMapper.countOpenHandledServiceApplications(userId, areaId) > 0);
            if (hasOrphanedService) {
                throw new BusinessException(ErrorCode.CONFLICT, "被移除社区工作人员仍有未完成服务申请，不能撤销责任范围");
            }
        }

        ensureUpdated(gridMapper.touchVersion(areaId, request.version()));
        gridMapper.endActiveAssignments(areaId, assignmentType);
        long operatorId = operator.id();
        assignments.forEach(assignment -> gridMapper.insertAssignment(
                areaId, assignment.userId(), assignmentType, assignment.primary(), operatorId
        ));
        return toDetail(requireArea(areaId));
    }

    public List<AreaOption> findCommunityOptions() {
        AuthenticatedUser user = dataScopeService.currentUser();
        List<GridMapper.AreaOptionRow> rows = user.roles().contains(RoleCodes.SYSTEM_ADMIN)
                ? gridMapper.findAllCommunities()
                : gridMapper.findAccessibleCommunities(user.id());
        return rows.stream()
                .map(row -> new AreaOption(row.id().toString(), row.code(), row.name()))
                .toList();
    }

    public List<WorkerOption> findWorkerOptions() {
        return findAssigneeOptions(RoleCodes.GRID_WORKER);
    }

    public List<WorkerOption> findCommunityStaffOptions() {
        return findAssigneeOptions(RoleCodes.COMMUNITY_STAFF);
    }

    private List<WorkerOption> findAssigneeOptions(String roleCode) {
        return gridMapper.findAssigneeOptions(roleCode).stream()
                .map(row -> new WorkerOption(row.id().toString(), row.username(), row.realName()))
                .toList();
    }

    public PageResponse<GridSummary> findPage(
            String areaType,
            String keyword,
            String status,
            int page,
            int size
    ) {
        DataScope scope = dataScopeService.currentScope();
        boolean allAccess = scope.type() == DataScopeType.ALL;
        List<Long> gridIds = new ArrayList<>(scope.gridIds());
        String normalizedAreaType = normalizeAreaType(areaType);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        String normalizedStatus = normalizeStatus(status);

        List<GridSummary> items = gridMapper.findPage(
                        normalizedAreaType,
                        normalizedKeyword,
                        normalizedStatus,
                        allAccess,
                        gridIds,
                        (page - 1) * size,
                        size
                ).stream()
                .map(this::toSummary)
                .toList();
        long total = gridMapper.count(
                normalizedAreaType, normalizedKeyword, normalizedStatus, allAccess, gridIds
        );
        return new PageResponse<>(items, total, page, size);
    }

    private String normalizeAreaType(String areaType) {
        String normalized = normalizeText(areaType);
        if (normalized == null) {
            return "GRID";
        }
        if (!List.of("GRID", "COMMUNITY").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知区域类型");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim();
        if (!normalized.equals("ENABLED") && !normalized.equals("DISABLED")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知网格状态");
        }
        return normalized;
    }

    private GridSummary toSummary(GridMapper.GridRow row) {
        return new GridSummary(
                row.id().toString(),
                row.communityId() == null ? null : row.communityId().toString(),
                row.communityName(),
                row.areaCode(),
                row.areaName(),
                row.areaType(),
                row.address(),
                row.status(),
                row.version()
        );
    }

    private GridDetail toDetail(GridMapper.GridDetailRow row) {
        String assignmentType = "COMMUNITY".equals(row.areaType())
                ? RoleCodes.COMMUNITY_STAFF
                : RoleCodes.GRID_WORKER;
        List<GridAssignmentView> assignments = gridMapper.findActiveAssignments(row.id(), assignmentType).stream()
                .map(item -> new GridAssignmentView(
                        item.userId().toString(), item.username(), item.realName(), item.primaryFlag()
                ))
                .toList();
        return new GridDetail(
                row.id().toString(),
                row.communityId() == null ? null : row.communityId().toString(),
                row.areaCode(), row.areaName(), row.areaType(), row.address(),
                row.centerLongitude(), row.centerLatitude(), row.boundaryGeojson(),
                row.status(), row.version(), assignments
        );
    }

    private GridMapper.GridDetailRow requireArea(long id) {
        GridMapper.GridDetailRow row = gridMapper.findById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "社区或网格不存在");
        }
        return row;
    }

    private void requireAreaReadAccess(GridMapper.GridDetailRow row) {
        if ("GRID".equals(row.areaType())) {
            dataScopeService.requireGridAccess(row.id());
        } else {
            requireCommunityReadAccess(row.id(), dataScopeService.currentUser());
        }
    }

    private void requireAreaWriteAccess(GridMapper.GridDetailRow row) {
        AuthenticatedUser operator = dataScopeService.currentUser();
        if ("GRID".equals(row.areaType())) {
            dataScopeService.requireGridAccess(row.id());
        } else {
            requireCommunityManageAccess(row.id(), operator);
        }
    }

    private void requireCommunityReadAccess(long communityId, AuthenticatedUser user) {
        if (user.roles().contains(RoleCodes.SYSTEM_ADMIN)) {
            return;
        }
        boolean accessible = gridMapper.findAccessibleCommunities(user.id()).stream()
                .anyMatch(row -> row.id().equals(communityId));
        if (!accessible) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该社区");
        }
    }

    private void requireCommunityManageAccess(long communityId, AuthenticatedUser user) {
        if (user.roles().contains(RoleCodes.SYSTEM_ADMIN)) {
            return;
        }
        if (!user.roles().contains(RoleCodes.COMMUNITY_STAFF)
                || gridMapper.countCommunityStaffAccess(user.id(), communityId) != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权维护该社区");
        }
    }

    private String normalizeJson(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        try {
            objectMapper.readTree(normalized);
            return normalized;
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "GeoJSON 不是合法 JSON");
        }
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private record ResolvedAssignment(long userId, boolean primary) {
    }
}
