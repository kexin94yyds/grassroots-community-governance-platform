package com.cunzhi.governance.task.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record TaskCreateRequest(
        @NotBlank String gridId,
        @NotBlank @Pattern(regexp = "ROUTINE_INSPECTION|OTHER") String taskType,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 10000) String description,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
        @NotBlank String assigneeUserId,
        @Future LocalDateTime dueAt
) {
}
