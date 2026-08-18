package com.cunzhi.governance.dashboard.dto;

import java.util.List;

public record DashboardOverview(
        long gridCount,
        long residentCount,
        long keyPopulationCount,
        long pendingEventCount,
        long processingEventCount,
        long pendingReviewEventCount,
        long closedEventCount,
        List<DashboardGridEventStat> gridEventStats,
        List<DashboardCategoryStat> categoryStats,
        List<DashboardRecentEvent> recentEvents
) {
    public DashboardOverview {
        gridEventStats = List.copyOf(gridEventStats);
        categoryStats = List.copyOf(categoryStats);
        recentEvents = List.copyOf(recentEvents);
    }
}
