package com.cunzhi.governance.resident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record HouseholdUpdateRequest(
        @Size(max = 50) String buildingNo,
        @Size(max = 50) String unitNo,
        @Size(max = 50) String roomNo,
        @NotBlank @Size(max = 255) String address,
        @NotNull @PositiveOrZero Integer version
) {
}
