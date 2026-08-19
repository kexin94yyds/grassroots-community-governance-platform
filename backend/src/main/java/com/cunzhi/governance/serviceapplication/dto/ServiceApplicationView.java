package com.cunzhi.governance.serviceapplication.dto;

import java.time.LocalDateTime;

public record ServiceApplicationView(
        String id,
        String applicationNo,
        String serviceCatalogId,
        String serviceCatalogName,
        String residentId,
        String residentName,
        String gridId,
        String gridName,
        String requestContent,
        LocalDateTime appointmentAt,
        String status,
        String handlerUserId,
        String handlerName,
        String resultSummary,
        Integer rating,
        String ratingRemark,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        int version
) {
}
