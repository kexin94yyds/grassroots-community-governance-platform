package com.cunzhi.governance.event.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventDispatchRequest(
        @NotNull @PositiveOrZero Integer version,
        @NotBlank String assigneeUserId,
        @Size(max = 160) String taskTitle,
        @Size(max = 10000) String taskDescription,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
        @Future LocalDateTime dueAt,
        @Size(max = 1000) String remark
) {
}
