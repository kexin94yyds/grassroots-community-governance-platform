package com.cunzhi.governance.event.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EventActionRequest(
        @NotNull @PositiveOrZero Integer version,
        @Size(max = 1000) String remark,
        @Size(max = 1000) String reason
) {
}
