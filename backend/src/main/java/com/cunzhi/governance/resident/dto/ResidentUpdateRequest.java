package com.cunzhi.governance.resident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ResidentUpdateRequest(
        String householdId,
        @NotBlank @Size(max = 80) String realName,
        @Pattern(regexp = "MALE|FEMALE|OTHER|UNKNOWN") String gender,
        LocalDate birthDate,
        @Pattern(regexp = "^$|^(\\d{15}|\\d{17}[0-9Xx])$", message = "身份证号格式不正确") String idCard,
        @Pattern(regexp = "^$|^\\+?[0-9 -]{7,30}$", message = "手机号格式不正确") String phone,
        @NotBlank @Size(max = 255) String address,
        @NotNull Boolean isHouseholder,
        @Size(max = 20) List<@NotBlank @Size(max = 40) String> specialGroupTags,
        @Size(max = 500) String remark,
        @NotNull @PositiveOrZero Integer version
) {
    public ResidentUpdateRequest {
        specialGroupTags = specialGroupTags == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(specialGroupTags));
    }
}
