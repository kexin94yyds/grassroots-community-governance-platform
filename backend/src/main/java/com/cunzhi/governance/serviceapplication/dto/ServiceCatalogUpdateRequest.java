package com.cunzhi.governance.serviceapplication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ServiceCatalogUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull Integer sortNo,
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status,
        @NotNull @PositiveOrZero Integer version
) {
}
