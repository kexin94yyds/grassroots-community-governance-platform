package com.cunzhi.governance.grid.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GridUpdateRequest(
        @NotBlank @Size(max = 120) String areaName,
        @Size(max = 255) String address,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal centerLongitude,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal centerLatitude,
        @Size(max = 100000) String boundaryGeojson,
        @NotNull @PositiveOrZero Integer version
) {
}
