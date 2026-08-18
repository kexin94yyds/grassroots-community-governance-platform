package com.cunzhi.governance.system.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserSummary(
        String id,
        String username,
        String realName,
        String status,
        String accountType,
        String approvalStatus,
        String requestedResidentId,
        String requestedResidentName,
        List<String> roles,
        LocalDateTime lastLoginAt,
        int version
) {
    public UserSummary {
        roles = List.copyOf(roles);
    }
}
