package com.cunzhi.governance.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EventCategoryUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        @NotNull @PositiveOrZero Integer sortNo,
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED", message = "类别状态不正确") String status,
        @NotNull @PositiveOrZero Integer version
) {
}
