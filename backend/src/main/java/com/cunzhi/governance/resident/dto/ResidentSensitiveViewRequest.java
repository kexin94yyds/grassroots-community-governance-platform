package com.cunzhi.governance.resident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResidentSensitiveViewRequest(
        @NotBlank @Size(min = 5, max = 200) String purpose
) {
}
