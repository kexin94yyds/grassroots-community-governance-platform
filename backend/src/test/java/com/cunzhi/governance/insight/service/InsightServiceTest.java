package com.cunzhi.governance.insight.service;

import com.cunzhi.governance.insight.dto.ModuleInsights;
import com.cunzhi.governance.insight.mapper.InsightMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.service.DataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock
    private InsightMapper insightMapper;
    @Mock
    private DataScopeService dataScopeService;

    @Test
    void mapsUserTotalsAndRoleBreakdown() {
        when(insightMapper.users()).thenReturn(new InsightMapper.UserInsightRow(
                12, 9, 2, 1, 7, 2, 4, 6
        ));

        ModuleInsights.UserInsight insight = service().users();

        assertThat(insight.total()).isEqualTo(12);
        assertThat(insight.roles()).containsExactly(
                new ModuleInsights.Breakdown("SYSTEM_ADMIN", 2),
                new ModuleInsights.Breakdown("COMMUNITY_STAFF", 4),
                new ModuleInsights.Breakdown("GRID_WORKER", 6)
        );
    }

    @Test
    void buildsScopedResponsibilityTopologyWithoutDuplicatingGrids() {
        when(dataScopeService.currentScope()).thenReturn(
                new DataScope(DataScopeType.COMMUNITY, Set.of(9L, 3L))
        );
        when(insightMapper.gridTopology(false, List.of(3L, 9L))).thenReturn(List.of(
                gridRow(1, "COM-01", "第一社区", 10, "GRID-01", "第一网格", "ENABLED", true, 20L, "甲", true),
                gridRow(1, "COM-01", "第一社区", 10, "GRID-01", "第一网格", "ENABLED", true, 21L, "乙", false),
                gridRow(1, "COM-01", "第一社区", 11, "GRID-02", "第二网格", "DISABLED", false, null, null, null)
        ));

        ModuleInsights.GridInsight insight = service().grids();

        verify(insightMapper).gridTopology(false, List.of(3L, 9L));
        assertThat(insight.communityCount()).isEqualTo(1);
        assertThat(insight.gridCount()).isEqualTo(2);
        assertThat(insight.enabledGridCount()).isEqualTo(1);
        assertThat(insight.assignedGridCount()).isEqualTo(1);
        assertThat(insight.unassignedGridCount()).isEqualTo(1);
        assertThat(insight.geoReadyGridCount()).isEqualTo(1);
        assertThat(insight.communities().get(0).status()).isEqualTo("ENABLED");
        ModuleInsights.GridNode firstGrid = insight.communities().get(0).grids().get(0);
        assertThat(firstGrid.workers()).hasSize(2);
        assertThat(firstGrid.centerLongitude()).isEqualByComparingTo("120.1234567");
        assertThat(firstGrid.centerLatitude()).isEqualByComparingTo("30.1234567");
    }

    @Test
    void appliesScopeToResidentEventAndTaskInsights() {
        when(dataScopeService.currentScope()).thenReturn(
                new DataScope(DataScopeType.GRID, Set.of(8L))
        );
        when(insightMapper.residents(false, List.of(8L))).thenReturn(
                new InsightMapper.ResidentInsightRow(5, 3, 4, 1, 0, 0, 2)
        );
        when(insightMapper.residentSpecialGroups(false, List.of(8L))).thenReturn(List.of(
                new InsightMapper.BreakdownRow("ELDERLY", 2)
        ));
        when(insightMapper.events(false, List.of(8L))).thenReturn(
                new InsightMapper.EventInsightRow(7, 1, 1, 1, 1, 1, 1, 1, 0, 1, 2, 3, 1)
        );
        when(insightMapper.tasks(false, List.of(8L))).thenReturn(
                new InsightMapper.TaskInsightRow(6, 1, 1, 1, 2, 1, 1, 2, 2, 1, 1)
        );

        ModuleInsights.ResidentInsight residents = service().residents();
        ModuleInsights.EventInsight events = service().events();
        ModuleInsights.TaskInsight tasks = service().tasks();

        assertThat(residents.specialGroups())
                .containsExactly(new ModuleInsights.Breakdown("ELDERLY", 2));
        assertThat(events.actionable()).isEqualTo(5);
        assertThat(tasks.overdue()).isEqualTo(1);
        verify(insightMapper).residents(false, List.of(8L));
        verify(insightMapper).events(false, List.of(8L));
        verify(insightMapper).tasks(false, List.of(8L));
    }

    @Test
    void excludesOutOfRangeCoordinatesFromGeoReadyCount() {
        when(dataScopeService.currentScope()).thenReturn(DataScope.all());
        when(insightMapper.gridTopology(true, List.of())).thenReturn(List.of(
                gridRowWithCoordinates(
                        1, "COM-01", "第一社区", 10, "GRID-01", "越界网格", "ENABLED",
                        new BigDecimal("181.0000000"), new BigDecimal("30.0000000"), null, null, null
                )
        ));

        ModuleInsights.GridInsight insight = service().grids();

        assertThat(insight.geoReadyGridCount()).isZero();
        assertThat(insight.communities().get(0).grids().get(0).geoReady()).isFalse();
    }

    @Test
    void passesAllAccessWithoutSyntheticGridIds() {
        when(dataScopeService.currentScope()).thenReturn(DataScope.all());
        when(insightMapper.events(true, List.of())).thenReturn(
                new InsightMapper.EventInsightRow(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        );

        service().events();

        verify(insightMapper).events(true, List.of());
    }

    private InsightService service() {
        return new InsightService(insightMapper, dataScopeService);
    }

    private InsightMapper.GridTopologyRow gridRow(
            long communityId,
            String communityCode,
            String communityName,
            long gridId,
            String gridCode,
            String gridName,
            String status,
            boolean geoReady,
            Long workerId,
            String workerName,
            Boolean primary
    ) {
        return gridRowWithCoordinates(
                communityId,
                communityCode,
                communityName,
                gridId,
                gridCode,
                gridName,
                status,
                geoReady ? new BigDecimal("120.1234567") : null,
                geoReady ? new BigDecimal("30.1234567") : null,
                workerId,
                workerName,
                primary
        );
    }

    private InsightMapper.GridTopologyRow gridRowWithCoordinates(
            long communityId,
            String communityCode,
            String communityName,
            long gridId,
            String gridCode,
            String gridName,
            String status,
            BigDecimal centerLongitude,
            BigDecimal centerLatitude,
            Long workerId,
            String workerName,
            Boolean primary
    ) {
        return new InsightMapper.GridTopologyRow(
                communityId,
                communityCode,
                communityName,
                "ENABLED",
                gridId,
                gridCode,
                gridName,
                status,
                "测试地址",
                centerLongitude,
                centerLatitude,
                workerId,
                workerName,
                primary
        );
    }
}
