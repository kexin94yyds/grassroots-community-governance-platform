package com.cunzhi.governance.resident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResidentEventRequest(
        @NotBlank String categoryId,
        @NotBlank @Size(min = 2, max = 160) String title,
        @NotBlank @Size(min = 5, max = 10000) String description,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String severity,
        @Size(max = 255) String address
) {
}

