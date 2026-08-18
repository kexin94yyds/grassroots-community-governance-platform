package com.cunzhi.governance.dashboard.service;

import com.cunzhi.governance.dashboard.dto.DashboardOverview;
import com.cunzhi.governance.dashboard.mapper.DashboardMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.service.DataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardMapper dashboardMapper;
    @Mock
    private DataScopeService dataScopeService;

    @Test
    void mapsScopedStatisticsWithPercentageValuesInZeroToOneHundredRange() {
        when(dataScopeService.currentScope()).thenReturn(new DataScope(DataScopeType.GRID, Set.of(7L)));
        when(dashboardMapper.overview(false, List.of(7L))).thenReturn(
                new DashboardMapper.DashboardRow(1, 8, 2, 1, 3, 1, 4)
        );
        when(dashboardMapper.findGridEventStats(false, List.of(7L))).thenReturn(List.of(
                new DashboardMapper.GridEventStatRow(7L, "GRD-007", "第七网格", 9, 4, 3)
        ));
        when(dashboardMapper.findCategoryStats(false, List.of(7L))).thenReturn(List.of(
                new DashboardMapper.CategoryStatRow(1L, "环境卫生", 3),
                new DashboardMapper.CategoryStatRow(2L, "安全隐患", 1)
        ));
        LocalDateTime reportedAt = LocalDateTime.of(2026, 8, 8, 10, 30);
        when(dashboardMapper.findRecentEvents(false, List.of(7L))).thenReturn(List.of(
                new DashboardMapper.RecentEventRow(
                        9L, "EVT-0009", "楼道堆物", "环境卫生", "第七网格",
                        "PROCESSING", "MEDIUM", reportedAt
                )
        ));

        DashboardOverview overview = service().overview();

        assertThat(overview.processingEventCount()).isEqualTo(3);
        assertThat(overview.gridEventStats()).singleElement().satisfies(item -> {
            assertThat(item.gridId()).isEqualTo("7");
            assertThat(item.eventCount()).isEqualTo(9);
            assertThat(item.completedWithDeadlineCount()).isEqualTo(4);
            assertThat(item.onTimeClosedCount()).isEqualTo(3);
            assertThat(item.onTimeCompletionRate()).isEqualByComparingTo("75.00");
            assertThat(item.onTimeCompletionRate()).isBetween(BigDecimal.ZERO, new BigDecimal("100.00"));
        });
        assertThat(overview.categoryStats()).extracting(item -> item.percentage())
                .containsExactly(new BigDecimal("75.00"), new BigDecimal("25.00"));
        assertThat(overview.recentEvents()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("9");
            assertThat(item.reportedAt()).isEqualTo(reportedAt);
        });
        verify(dashboardMapper).overview(false, List.of(7L));
        verify(dashboardMapper).findGridEventStats(false, List.of(7L));
        verify(dashboardMapper).findCategoryStats(false, List.of(7L));
        verify(dashboardMapper).findRecentEvents(false, List.of(7L));
    }

    @Test
    void returnsZeroPercentWhenNoCompletedDeadlineTaskOrCategoryEventExists() {
        when(dataScopeService.currentScope()).thenReturn(DataScope.all());
        when(dashboardMapper.overview(true, List.of())).thenReturn(
                new DashboardMapper.DashboardRow(1, 0, 0, 0, 0, 0, 0)
        );
        when(dashboardMapper.findGridEventStats(true, List.of())).thenReturn(List.of(
                new DashboardMapper.GridEventStatRow(7L, "GRD-007", "第七网格", 0, 0, 0)
        ));
        when(dashboardMapper.findCategoryStats(true, List.of())).thenReturn(List.of(
                new DashboardMapper.CategoryStatRow(1L, "环境卫生", 0)
        ));
        when(dashboardMapper.findRecentEvents(true, List.of())).thenReturn(List.of());

        DashboardOverview overview = service().overview();

        assertThat(overview.gridEventStats()).singleElement()
                .extracting(item -> item.onTimeCompletionRate())
                .isEqualTo(new BigDecimal("0.00"));
        assertThat(overview.categoryStats()).singleElement()
                .extracting(item -> item.percentage())
                .isEqualTo(new BigDecimal("0.00"));
        assertThat(overview.recentEvents()).isEmpty();
    }

    private DashboardService service() {
        return new DashboardService(dashboardMapper, dataScopeService);
    }
}
