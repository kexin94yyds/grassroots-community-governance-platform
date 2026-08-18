package com.cunzhi.governance.auth.dto;

public record RegistrationResponse(
        String username,
        String accountType,
        String approvalStatus,
        String message
) {
}

