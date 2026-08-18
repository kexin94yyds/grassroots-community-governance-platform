package com.cunzhi.governance.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EventCategoryCreateRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}", message = "类别编码格式不正确") String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        @PositiveOrZero Integer sortNo,
        @Pattern(regexp = "ENABLED|DISABLED", message = "类别状态不正确") String status
) {
}
