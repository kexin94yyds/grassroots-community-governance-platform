package com.cunzhi.governance.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UserStatusRequest(
        @NotNull Boolean enabled,
        @NotNull @PositiveOrZero Integer version
) {
}
