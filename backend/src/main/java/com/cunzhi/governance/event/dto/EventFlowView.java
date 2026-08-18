package com.cunzhi.governance.event.dto;

import java.time.LocalDateTime;

public record EventFlowView(
        String id,
        String eventId,
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
