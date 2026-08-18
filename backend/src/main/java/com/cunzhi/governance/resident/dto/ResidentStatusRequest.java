package com.cunzhi.governance.resident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record ResidentStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|MOVED|DECEASED|ARCHIVED") String status,
        @NotNull @PositiveOrZero Integer version
) {
}
