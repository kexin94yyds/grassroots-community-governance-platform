package com.cunzhi.governance.resident.dto;

import java.time.LocalDateTime;

/** Deliberately excludes encrypted values, hashes and any plaintext sensitive identifier. */
public record ResidentSensitiveAccessLogView(
        String id,
        String operatorUserId,
        String operatorName,
        String operatorUsername,
        String residentId,
        String residentNo,
        String residentName,
        String scopeGridId,
        String scopeGridCode,
        String scopeGridName,
        String action,
        String fieldType,
        String purpose,
        int resultCount,
        LocalDateTime createdAt
) {
}
