package com.cunzhi.governance.serviceapplication.dto;

import java.time.LocalDateTime;

public record ServiceApplicationFlowView(
        String id,
        String action,
        String fromStatus,
        String toStatus,
        String operatorUserId,
        String operatorName,
        String remark,
        LocalDateTime createdAt
) {
}
