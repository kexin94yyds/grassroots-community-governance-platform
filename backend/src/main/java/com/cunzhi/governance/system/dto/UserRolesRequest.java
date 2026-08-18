package com.cunzhi.governance.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record UserRolesRequest(
        @NotEmpty Set<@NotBlank @Size(max = 50) String> roleCodes,
        @NotNull @PositiveOrZero Integer version
) {
    public UserRolesRequest {
        roleCodes = roleCodes == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(roleCodes));
    }
}
