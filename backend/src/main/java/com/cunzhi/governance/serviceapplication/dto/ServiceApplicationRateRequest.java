package com.cunzhi.governance.serviceapplication.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ServiceApplicationRateRequest(
        @NotNull @PositiveOrZero Integer version,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 500) String remark
) {
}
