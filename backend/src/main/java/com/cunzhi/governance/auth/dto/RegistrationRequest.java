package com.cunzhi.governance.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank @Pattern(regexp = "STAFF|RESIDENT") String accountType,
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}", message = "只能包含字母、数字、点、下划线或连字符，长度3-64")
        String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 80) String realName,
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 -]{7,30}$", message = "手机号格式不正确")
        String phone,
        @Size(max = 32) String idCardNumber,
        @Size(max = 500) String note
) {
}
