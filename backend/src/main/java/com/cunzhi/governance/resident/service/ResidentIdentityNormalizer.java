package com.cunzhi.governance.resident.service;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;

import java.util.Locale;

public final class ResidentIdentityNormalizer {

    private ResidentIdentityNormalizer() {
    }

    public static String normalizeIdCard(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "身份证号不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[0-9]{15}|[0-9]{17}[0-9X]")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "身份证号格式不正确");
        }
        return normalized;
    }

    public static String normalizePhone(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手机号不能为空");
        }
        String normalized = value.trim().replaceAll("[\\s-]", "");
        if (!normalized.matches("\\+?[0-9]{7,20}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手机号格式不正确");
        }
        return normalized;
    }
}
