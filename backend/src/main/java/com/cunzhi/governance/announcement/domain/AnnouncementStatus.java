package com.cunzhi.governance.announcement.domain;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;

import java.util.EnumSet;
import java.util.Set;

public enum AnnouncementStatus {
    DRAFT,
    PUBLISHED,
    WITHDRAWN;

    public Set<AnnouncementStatus> allowedTargets() {
        return switch (this) {
            case DRAFT -> EnumSet.of(PUBLISHED, WITHDRAWN);
            case PUBLISHED -> EnumSet.of(WITHDRAWN);
            case WITHDRAWN -> EnumSet.noneOf(AnnouncementStatus.class);
        };
    }

    public void requireTransitionTo(AnnouncementStatus target) {
        if (!allowedTargets().contains(target)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "公告不能从 " + this + " 流转到 " + target);
        }
    }
}
