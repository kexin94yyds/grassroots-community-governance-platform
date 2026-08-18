package com.cunzhi.governance.insight.service;

import com.cunzhi.governance.insight.dto.ModuleInsights;
import com.cunzhi.governance.insight.mapper.InsightMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.service.DataScopeService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InsightService {

    private final InsightMapper insightMapper;
    private final DataScopeService dataScopeService;

    public InsightService(InsightMapper insightMapper, DataScopeService dataScopeService) {
        this.insightMapper = insightMapper;
        this.dataScopeService = dataScopeService;
    }

    public ModuleInsights.UserInsight users() {
        InsightMapper.UserInsightRow row = insightMapper.users();
        return new ModuleInsights.UserInsight(
                row.totalCount(),
                row.enabledCount(),
                row.disabledCount(),
                row.lockedCount(),
                row.loggedInLast30DaysCount(),
                List.of(
                        new ModuleInsights.Breakdown("SYSTEM_ADMIN", row.systemAdminCount()),
                        new ModuleInsights.Breakdown("COMMUNITY_STAFF", row.communityStaffCount()),
                        new ModuleInsights.Breakdown("GRID_WORKER", row.gridWorkerCount())
                )
        );
    }

    public ModuleInsights.GridInsight grids() {
        ScopeArguments scope = currentScope();
        List<InsightMapper.GridTopologyRow> rows =
                insightMapper.gridTopology(scope.allAccess(), scope.gridIds());

        Map<Long, CommunityBuilder> communityBuilders = new LinkedHashMap<>();
        for (InsightMapper.GridTopologyRow row : rows) {
            CommunityBuilder community = communityBuilders.computeIfAbsent(
                    row.communityId(),
                    ignored -> new CommunityBuilder(
                            id(row.communityId()),
                            row.communityCode(),
                            row.communityName(),
                            row.communityStatus()
                    )
            );
            GridBuilder grid = community.grids.computeIfAbsent(
                    row.gridId(),
                    ignored -> new GridBuilder(
                            id(row.gridId()),
                            row.gridCode(),
                            row.gridName(),
                            row.gridStatus(),
                            row.address(),
                            row.centerLongitude(),
                            row.centerLatitude()
                    )
            );
            if (row.workerId() != null) {
                grid.workers.add(new ModuleInsights.WorkerNode(
                        id(row.workerId()),
                        row.workerName(),
                        Boolean.TRUE.equals(row.primaryWorker())
                ));
            }
        }

        List<ModuleInsights.CommunityNode> communities = communityBuilders.values().stream()
                .map(CommunityBuilder::build)
                .toList();
        List<ModuleInsights.GridNode> grids = communities.stream()
                .flatMap(community -> community.grids().stream())
                .toList();
        long enabled = grids.stream().filter(grid -> "ENABLED".equals(grid.status())).count();
        long assigned = grids.stream().filter(grid -> !grid.workers().isEmpty()).count();
        long geoReady = grids.stream().filter(ModuleInsights.GridNode::geoReady).count();

        return new ModuleInsights.GridInsight(
                communities.size(),
                grids.size(),
                enabled,
                grids.size() - enabled,
                assigned,
                grids.size() - assigned,
                geoReady,
                communities
        );
    }

    public ModuleInsights.ResidentInsight residents() {
        ScopeArguments scope = currentScope();
        InsightMapper.ResidentInsightRow row =
                insightMapper.residents(scope.allAccess(), scope.gridIds());
        List<ModuleInsights.Breakdown> specialGroups =
                insightMapper.residentSpecialGroups(scope.allAccess(), scope.gridIds()).stream()
                        .map(item -> new ModuleInsights.Breakdown(item.breakdownKey(), item.itemCount()))
                        .toList();
        return new ModuleInsights.ResidentInsight(
                row.residentCount(),
                row.householdCount(),
                row.activeCount(),
                row.movedCount(),
                row.deceasedCount(),
                row.archivedCount(),
                row.keyPopulationCount(),
                specialGroups
        );
    }

    public ModuleInsights.EventInsight events() {
        ScopeArguments scope = currentScope();
        InsightMapper.EventInsightRow row =
                insightMapper.events(scope.allAccess(), scope.gridIds());
        long actionable = row.reportedCount()
                + row.acceptedCount()
                + row.assignedCount()
                + row.processingCount()
                + row.pendingReviewCount();
        return new ModuleInsights.EventInsight(
                row.totalCount(),
                actionable,
                List.of(
                        new ModuleInsights.Breakdown("REPORTED", row.reportedCount()),
                        new ModuleInsights.Breakdown("ACCEPTED", row.acceptedCount()),
                        new ModuleInsights.Breakdown("ASSIGNED", row.assignedCount()),
                        new ModuleInsights.Breakdown("PROCESSING", row.processingCount()),
                        new ModuleInsights.Breakdown("PENDING_REVIEW", row.pendingReviewCount()),
                        new ModuleInsights.Breakdown("CLOSED", row.closedCount()),
                        new ModuleInsights.Breakdown("REJECTED", row.rejectedCount()),
                        new ModuleInsights.Breakdown("CANCELLED", row.cancelledCount())
                ),
                priorityBreakdown(row.lowCount(), row.mediumCount(), row.highCount(), row.urgentCount())
        );
    }

    public ModuleInsights.TaskInsight tasks() {
        ScopeArguments scope = currentScope();
        InsightMapper.TaskInsightRow row =
                insightMapper.tasks(scope.allAccess(), scope.gridIds());
        return new ModuleInsights.TaskInsight(
                row.totalCount(),
                row.overdueCount(),
                List.of(
                        new ModuleInsights.Breakdown("PENDING_ACCEPT", row.pendingAcceptCount()),
                        new ModuleInsights.Breakdown("PROCESSING", row.processingCount()),
                        new ModuleInsights.Breakdown("PENDING_REVIEW", row.pendingReviewCount()),
                        new ModuleInsights.Breakdown("COMPLETED", row.completedCount()),
                        new ModuleInsights.Breakdown("CANCELLED", row.cancelledCount())
                ),
                priorityBreakdown(row.lowCount(), row.mediumCount(), row.highCount(), row.urgentCount())
        );
    }

    private List<ModuleInsights.Breakdown> priorityBreakdown(
            long low,
            long medium,
            long high,
            long urgent
    ) {
        return List.of(
                new ModuleInsights.Breakdown("LOW", low),
                new ModuleInsights.Breakdown("MEDIUM", medium),
                new ModuleInsights.Breakdown("HIGH", high),
                new ModuleInsights.Breakdown("URGENT", urgent)
        );
    }

    private ScopeArguments currentScope() {
        DataScope scope = dataScopeService.currentScope();
        return new ScopeArguments(
                scope.type() == DataScopeType.ALL,
                scope.gridIds().stream().sorted().toList()
        );
    }

    private String id(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ScopeArguments(boolean allAccess, List<Long> gridIds) {
    }

    private static final class CommunityBuilder {
        private final String id;
        private final String code;
        private final String name;
        private final String status;
        private final Map<Long, GridBuilder> grids = new LinkedHashMap<>();

        private CommunityBuilder(String id, String code, String name, String status) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.status = status;
        }

        private ModuleInsights.CommunityNode build() {
            return new ModuleInsights.CommunityNode(
                    id,
                    code,
                    name,
                    status,
                    grids.values().stream().map(GridBuilder::build).toList()
            );
        }
    }

    private static final class GridBuilder {
        private final String id;
        private final String code;
        private final String name;
        private final String status;
        private final String address;
        private final BigDecimal centerLongitude;
        private final BigDecimal centerLatitude;
        private final boolean geoReady;
        private final List<ModuleInsights.WorkerNode> workers = new ArrayList<>();

        private GridBuilder(
                String id,
                String code,
                String name,
                String status,
                String address,
                BigDecimal centerLongitude,
                BigDecimal centerLatitude
        ) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.status = status;
            this.address = address;
            this.centerLongitude = centerLongitude;
            this.centerLatitude = centerLatitude;
            this.geoReady = validCoordinates(centerLongitude, centerLatitude);
        }

        private ModuleInsights.GridNode build() {
            return new ModuleInsights.GridNode(
                    id,
                    code,
                    name,
                    status,
                    address,
                    centerLongitude,
                    centerLatitude,
                    geoReady,
                    workers
            );
        }
    }

    private static boolean validCoordinates(BigDecimal longitude, BigDecimal latitude) {
        return longitude != null
                && latitude != null
                && longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                && longitude.compareTo(BigDecimal.valueOf(180)) <= 0
                && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                && latitude.compareTo(BigDecimal.valueOf(90)) <= 0;
    }
}
