package com.cunzhi.governance.event.domain;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;

import java.util.EnumSet;
import java.util.Set;

public enum EventStatus {
    REPORTED,
    ACCEPTED,
    ASSIGNED,
    PROCESSING,
    PENDING_REVIEW,
    CLOSED,
    REJECTED,
    CANCELLED;

    public Set<EventStatus> allowedTargets() {
        return switch (this) {
            case REPORTED -> EnumSet.of(ACCEPTED, REJECTED, CANCELLED);
            case ACCEPTED -> EnumSet.of(ASSIGNED, CANCELLED);
            case ASSIGNED -> EnumSet.of(PROCESSING);
            case PROCESSING -> EnumSet.of(PENDING_REVIEW);
            case PENDING_REVIEW -> EnumSet.of(CLOSED, PROCESSING);
            case CLOSED, REJECTED, CANCELLED -> EnumSet.noneOf(EventStatus.class);
        };
    }

    public void requireTransitionTo(EventStatus target) {
        if (!allowedTargets().contains(target)) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "事件不能从 " + this + " 流转到 " + target
            );
        }
    }
}
