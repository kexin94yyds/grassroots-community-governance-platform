package com.cunzhi.governance.task.domain;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;

import java.util.EnumSet;
import java.util.Set;

public enum TaskStatus {
    PENDING_ACCEPT,
    PROCESSING,
    PENDING_REVIEW,
    COMPLETED,
    CANCELLED;

    public Set<TaskStatus> allowedTargets() {
        return switch (this) {
            case PENDING_ACCEPT -> EnumSet.of(PROCESSING, CANCELLED);
            case PROCESSING -> EnumSet.of(PENDING_REVIEW);
            case PENDING_REVIEW -> EnumSet.of(COMPLETED, PROCESSING);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(TaskStatus.class);
        };
    }

    public void requireTransitionTo(TaskStatus target) {
        if (!allowedTargets().contains(target)) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "任务不能从 " + this + " 流转到 " + target
            );
        }
    }
}
