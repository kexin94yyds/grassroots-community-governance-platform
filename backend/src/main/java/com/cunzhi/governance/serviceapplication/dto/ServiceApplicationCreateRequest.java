package com.cunzhi.governance.serviceapplication.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ServiceApplicationCreateRequest(
        @NotBlank String serviceCatalogId,
        @NotBlank @Size(min = 2, max = 10000) String requestContent,
        @FutureOrPresent LocalDateTime appointmentAt,
        @Size(max = 64) @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestToken
) {
}
