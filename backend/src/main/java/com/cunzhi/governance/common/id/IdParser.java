package com.cunzhi.governance.common.id;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;

public final class IdParser {

    private IdParser() {
    }

    public static long parse(String value, String fieldName) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + "必须是正整数");
        }
    }
}
