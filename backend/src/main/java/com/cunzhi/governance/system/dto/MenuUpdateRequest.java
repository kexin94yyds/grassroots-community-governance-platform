package com.cunzhi.governance.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MenuUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 80) String icon,
        @Min(0) int sortNo,
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status,
        @Min(0) int version
) {
}
