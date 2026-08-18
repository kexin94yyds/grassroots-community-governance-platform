package com.cunzhi.governance.dashboard.dto;

import java.time.LocalDateTime;

public record DashboardRecentEvent(
        String id,
        String eventNo,
        String title,
        String categoryName,
        String gridName,
        String status,
        String severity,
        LocalDateTime reportedAt
) {
}
