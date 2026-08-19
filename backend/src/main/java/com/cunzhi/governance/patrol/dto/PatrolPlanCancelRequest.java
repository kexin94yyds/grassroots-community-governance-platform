package com.cunzhi.governance.patrol.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PatrolPlanCancelRequest(
        @NotNull @PositiveOrZero Integer version,
        @NotBlank @Size(max = 1000) String reason
) {
}
