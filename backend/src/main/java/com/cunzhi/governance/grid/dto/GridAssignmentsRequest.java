package com.cunzhi.governance.grid.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record GridAssignmentsRequest(
        @NotNull @PositiveOrZero Integer version,
        @NotEmpty List<@NotNull @Valid Assignment> assignments
) {
    public GridAssignmentsRequest {
        assignments = assignments == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(assignments));
    }

    public record Assignment(
            @NotBlank String userId,
            @NotNull Boolean isPrimary
    ) {
    }
}
