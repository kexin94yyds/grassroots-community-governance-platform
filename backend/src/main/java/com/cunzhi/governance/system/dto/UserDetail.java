package com.cunzhi.governance.system.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserDetail(
        String id,
        String username,
        String realName,
        String phone,
        String status,
        String accountType,
        String approvalStatus,
        String requestedResidentId,
        String requestedResidentName,
        String registrationNote,
        String rejectionReason,
        LocalDateTime reviewedAt,
        List<String> roles,
        LocalDateTime lastLoginAt,
        int version
) {
    public UserDetail {
        roles = List.copyOf(roles);
    }
}
