package com.cunzhi.governance.serviceapplication.domain;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;

import java.util.EnumSet;
import java.util.Set;

public enum ServiceApplicationStatus {
    SUBMITTED,
    ACCEPTED,
    PROCESSING,
    COMPLETED,
    REJECTED,
    CANCELLED;

    public Set<ServiceApplicationStatus> allowedTargets() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(ACCEPTED, REJECTED, CANCELLED);
            case ACCEPTED -> EnumSet.of(PROCESSING);
            case PROCESSING -> EnumSet.of(COMPLETED);
            case COMPLETED, REJECTED, CANCELLED -> EnumSet.noneOf(ServiceApplicationStatus.class);
        };
    }

    public void requireTransitionTo(ServiceApplicationStatus target) {
        if (!allowedTargets().contains(target)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "服务申请不能从 " + this + " 流转到 " + target);
        }
    }
}
