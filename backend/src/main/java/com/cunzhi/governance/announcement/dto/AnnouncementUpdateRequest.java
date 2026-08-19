package com.cunzhi.governance.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AnnouncementUpdateRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 20000) String content,
        @NotNull Boolean pinned,
        @NotNull @PositiveOrZero Integer version
) {
}
