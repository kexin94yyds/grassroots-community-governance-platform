package com.cunzhi.governance.resident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ResidentPortalProfileUpdateRequest(
        @Pattern(regexp = "^$|^\\+?[0-9 -]{7,30}$", message = "手机号格式不正确") String phone,
        @NotBlank @Size(max = 255) String address,
        @NotNull @PositiveOrZero Integer version
) {
}
