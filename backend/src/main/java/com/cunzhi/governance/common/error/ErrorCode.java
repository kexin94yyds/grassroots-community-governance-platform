package com.cunzhi.governance.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST, "请求参数错误"),
    VALIDATION_ERROR("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    UNAUTHENTICATED("UNAUTHENTICATED", HttpStatus.UNAUTHORIZED, "请先登录"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "无权执行该操作"),
    PASSWORD_CHANGE_REQUIRED("PASSWORD_CHANGE_REQUIRED", HttpStatus.FORBIDDEN, "请先修改临时密码"),
    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND, "请求的资源不存在"),
    CONFLICT("CONFLICT", HttpStatus.CONFLICT, "当前数据状态冲突"),
    INVALID_STATE_TRANSITION("INVALID_STATE_TRANSITION", HttpStatus.CONFLICT, "非法状态流转"),
    OPTIMISTIC_LOCK_CONFLICT("OPTIMISTIC_LOCK_CONFLICT", HttpStatus.CONFLICT, "数据已被他人更新，请刷新后重试"),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
