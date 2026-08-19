package com.cunzhi.governance.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AnnouncementCreateRequest(
        @NotBlank @Pattern(regexp = "GLOBAL|COMMUNITY") String audienceScope,
        @Size(max = 40) String communityId,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 20000) String content,
        @NotNull Boolean pinned
) {
}
