package com.cunzhi.governance.task.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record TaskActionRequest(
        @NotNull @PositiveOrZero Integer version,
        @PositiveOrZero Integer eventVersion,
        Boolean approved,
        @Size(max = 10000) String handlingResult,
        @Size(max = 20)
        List<@NotBlank @Pattern(regexp = "[1-9]\\d*", message = "必须是正整数字符串") String> attachmentIds,
        @Size(max = 1000) String remark,
        @Size(max = 1000) String reason
) {
    public TaskActionRequest {
        attachmentIds = attachmentIds == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(attachmentIds));
    }
}
