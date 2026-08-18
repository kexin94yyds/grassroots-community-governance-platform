package com.cunzhi.governance.common.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        OffsetDateTime timestamp
) {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "操作成功", data, OffsetDateTime.now(BUSINESS_ZONE));
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null, OffsetDateTime.now(BUSINESS_ZONE));
    }
}
