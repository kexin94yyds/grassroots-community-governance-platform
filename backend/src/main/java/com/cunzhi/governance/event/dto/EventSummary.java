package com.cunzhi.governance.event.dto;

import java.time.LocalDateTime;

public record EventSummary(
        String id,
        String eventNo,
        String categoryId,
        String categoryName,
        String gridId,
        String gridName,
        String title,
        String description,
        String address,
        String reportChannel,
        String severity,
        String status,
        String assignedToUserId,
        String assignedToName,
        String resultSummary,
        LocalDateTime reportedAt,
        int version
) {
}
