package com.cunzhi.governance.grid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record GridStatusRequest(
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status,
        @NotNull @PositiveOrZero Integer version
) {
}
