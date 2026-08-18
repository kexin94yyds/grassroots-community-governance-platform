package com.cunzhi.governance.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record RegistrationReviewRequest(
        @NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision,
        Set<@NotBlank @Size(max = 50) String> roleCodes,
        @Size(max = 500) String reason,
        @NotNull @PositiveOrZero Integer version
) {
    public RegistrationReviewRequest {
        roleCodes = roleCodes == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(roleCodes));
    }
}

