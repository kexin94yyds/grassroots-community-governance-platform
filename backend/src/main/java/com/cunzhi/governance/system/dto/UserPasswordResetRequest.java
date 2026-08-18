package com.cunzhi.governance.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserPasswordResetRequest(
        @NotBlank @Size(min = 8, max = 128) String temporaryPassword,
        @NotNull Integer version
) {
}
