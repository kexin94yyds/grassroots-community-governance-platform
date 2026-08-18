package com.cunzhi.governance.resident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HouseholdCreateRequest(
        @NotBlank String gridId,
        @Size(max = 50) String buildingNo,
        @Size(max = 50) String unitNo,
        @Size(max = 50) String roomNo,
        @NotBlank @Size(max = 255) String address
) {
}
