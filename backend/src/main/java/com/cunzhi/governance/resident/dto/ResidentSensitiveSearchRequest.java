package com.cunzhi.governance.resident.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResidentSensitiveSearchRequest(
        @NotBlank @Pattern(regexp = "ID_CARD|PHONE") String type,
        @NotBlank @Size(max = 64) String value,
        String gridId,
        @Pattern(regexp = "ACTIVE|MOVED|DECEASED|ARCHIVED") String status,
        @Min(1) int page,
        @Min(1) @Max(100) int size
) {
}
