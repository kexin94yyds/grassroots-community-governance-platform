package com.cunzhi.governance.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank @Size(min = 8, max = 128) String oldPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {
}
