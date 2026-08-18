package com.cunzhi.governance.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record UserCreateRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}", message = "只能包含字母、数字、点、下划线或连字符，长度3-64")
        String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 80) String realName,
        @Pattern(regexp = "^$|^\\+?[0-9-]{7,20}$", message = "手机号格式不正确") String phone,
        @NotEmpty Set<@NotBlank @Size(max = 50) String> roleCodes
) {
    public UserCreateRequest {
        roleCodes = roleCodes == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(roleCodes));
    }
}
