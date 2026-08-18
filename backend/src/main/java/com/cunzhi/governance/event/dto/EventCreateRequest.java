package com.cunzhi.governance.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EventCreateRequest(
        @NotBlank String categoryId,
        @NotBlank String gridId,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 10000) String description,
        @NotBlank @Pattern(regexp = "WEB|PHONE|ONSITE|OTHER") String reportChannel,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String severity,
        @Size(max = 255) String address,
        @Size(max = 80) String reporterName
) {
}
