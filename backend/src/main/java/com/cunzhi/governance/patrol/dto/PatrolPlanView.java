package com.cunzhi.governance.patrol.dto;

import java.time.LocalDateTime;

public record PatrolPlanView(
        String id,
        String planNo,
        String gridId,
        String gridName,
        String title,
        String inspectionContent,
        LocalDateTime scheduledAt,
        LocalDateTime dueAt,
        String assigneeUserId,
        String assigneeName,
        String status,
        String taskId,
        String taskNo,
        String taskStatus,
        Integer taskVersion,
        String createdBy,
        String createdByName,
        LocalDateTime createdAt,
        int version
) {
}
