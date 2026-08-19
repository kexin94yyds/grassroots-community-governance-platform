package com.cunzhi.governance.announcement.dto;

import java.time.LocalDateTime;

public record AnnouncementFlowView(
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
