package com.cunzhi.governance.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank @Size(max = 80) String realName,
        @Pattern(regexp = "^$|^\\+?[0-9-]{7,20}$", message = "手机号格式不正确") String phone,
        @NotNull @PositiveOrZero Integer version
) {
}
