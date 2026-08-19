package com.cunzhi.governance.patrol.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record PatrolPlanCreateRequest(
        @NotBlank String gridId,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 10000) String inspectionContent,
        @FutureOrPresent LocalDateTime scheduledAt,
        LocalDateTime dueAt,
        @NotBlank String assigneeUserId,
        @Size(max = 20) String priority
) {
}
