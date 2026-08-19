package com.cunzhi.governance.serviceapplication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ServiceCatalogCreateRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,49}") String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        Integer sortNo,
        @Pattern(regexp = "ENABLED|DISABLED") String status
) {
}
