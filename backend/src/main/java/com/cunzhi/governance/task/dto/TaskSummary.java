package com.cunzhi.governance.task.dto;

import java.time.LocalDateTime;

public record TaskSummary(
        String id,
        String taskNo,
        String sourceEventId,
        String sourceEventNo,
        String gridId,
        String gridName,
        String taskType,
        String title,
        String description,
        String priority,
        String status,
        String dispatcherUserId,
        String dispatcherName,
        String assigneeUserId,
        String assigneeName,
        LocalDateTime dueAt,
        LocalDateTime assignedAt,
        String handlingResult,
        String reviewRemark,
        int version
) {
}
