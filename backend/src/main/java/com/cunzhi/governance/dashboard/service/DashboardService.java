package com.cunzhi.governance.dashboard.service;

import com.cunzhi.governance.dashboard.dto.DashboardOverview;
import com.cunzhi.governance.dashboard.dto.DashboardCategoryStat;
import com.cunzhi.governance.dashboard.dto.DashboardGridEventStat;
import com.cunzhi.governance.dashboard.dto.DashboardRecentEvent;
import com.cunzhi.governance.dashboard.mapper.DashboardMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.service.DataScopeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class DashboardService {

    private final DashboardMapper dashboardMapper;
    private final DataScopeService dataScopeService;

    public DashboardService(DashboardMapper dashboardMapper, DataScopeService dataScopeService) {
        this.dashboardMapper = dashboardMapper;
        this.dataScopeService = dataScopeService;
    }

    public DashboardOverview overview() {
        DataScope scope = dataScopeService.currentScope();
        DashboardMapper.DashboardRow row = dashboardMapper.overview(
                scope.type() == DataScopeType.ALL,
                new ArrayList<>(scope.gridIds())
        );
        boolean allAccess = scope.type() == DataScopeType.ALL;
        List<Long> gridIds = new ArrayList<>(scope.gridIds());
        List<DashboardMapper.GridEventStatRow> gridRows = dashboardMapper.findGridEventStats(allAccess, gridIds);
        List<DashboardMapper.CategoryStatRow> categoryRows = dashboardMapper.findCategoryStats(allAccess, gridIds);
        long categoryTotal = categoryRows.stream().mapToLong(DashboardMapper.CategoryStatRow::eventCount).sum();
        return new DashboardOverview(
                row.gridCount(), row.residentCount(), row.keyPopulationCount(),
                row.pendingEventCount(), row.processingEventCount(),
                row.pendingReviewEventCount(), row.closedEventCount(),
                gridRows.stream().map(item -> new DashboardGridEventStat(
                        item.gridId().toString(), item.gridCode(), item.gridName(), item.eventCount(),
                        item.completedWithDeadlineCount(), item.onTimeClosedCount(),
                        percentage(item.onTimeClosedCount(), item.completedWithDeadlineCount())
                )).toList(),
                categoryRows.stream().map(item -> new DashboardCategoryStat(
                        item.categoryId().toString(), item.categoryName(), item.eventCount(),
                        percentage(item.eventCount(), categoryTotal)
                )).toList(),
                dashboardMapper.findRecentEvents(allAccess, gridIds).stream().map(item -> new DashboardRecentEvent(
                        item.id().toString(), item.eventNo(), item.title(), item.categoryName(), item.gridName(),
                        item.status(), item.severity(), item.reportedAt()
                )).toList()
        );
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
