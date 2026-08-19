package com.cunzhi.governance.serviceapplication.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ServiceApplicationActionRequest(
        @NotNull @PositiveOrZero Integer version,
        @Size(max = 10000) String resultSummary,
        @Size(max = 1000) String remark
) {
}
