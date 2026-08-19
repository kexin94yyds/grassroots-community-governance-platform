package com.cunzhi.governance.announcement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AnnouncementActionRequest(
        @NotNull @PositiveOrZero Integer version,
        @Size(max = 1000) String remark,
        @Size(max = 1000) String reason
) {
}
