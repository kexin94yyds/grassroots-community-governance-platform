package com.cunzhi.governance.dashboard.dto;

import java.math.BigDecimal;

public record DashboardGridEventStat(
        String gridId,
        String gridCode,
        String gridName,
        long eventCount,
        long completedWithDeadlineCount,
        long onTimeClosedCount,
        BigDecimal onTimeCompletionRate
) {
}
