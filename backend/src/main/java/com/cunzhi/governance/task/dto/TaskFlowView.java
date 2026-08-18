package com.cunzhi.governance.task.dto;

import java.time.LocalDateTime;

public record TaskFlowView(
        String id,
        String taskId,
        String action,
        String fromStatus,
        String toStatus,
        String operatorUserId,
        String operatorName,
        String remark,
        LocalDateTime createdAt
) {
}
