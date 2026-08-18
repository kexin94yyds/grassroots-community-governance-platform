package com.cunzhi.governance.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoleUpdateRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 255) String description,
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status,
        @NotEmpty Set<@Pattern(regexp = "[1-9]\\d*") String> menuIds,
        @Min(0) int version
) {
    public RoleUpdateRequest {
        menuIds = menuIds == null ? Set.of() : Set.copyOf(menuIds);
    }
}
