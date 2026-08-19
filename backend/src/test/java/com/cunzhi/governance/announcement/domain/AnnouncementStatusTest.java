package com.cunzhi.governance.announcement.domain;

import com.cunzhi.governance.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnnouncementStatusTest {

    @Test
    void permitsDraftPublishAndPublishedWithdraw() {
        assertDoesNotThrow(() -> AnnouncementStatus.DRAFT.requireTransitionTo(AnnouncementStatus.PUBLISHED));
        assertDoesNotThrow(() -> AnnouncementStatus.PUBLISHED.requireTransitionTo(AnnouncementStatus.WITHDRAWN));
    }

    @Test
    void rejectsReactivatingWithdrawnAnnouncement() {
        assertThrows(BusinessException.class,
                () -> AnnouncementStatus.WITHDRAWN.requireTransitionTo(AnnouncementStatus.PUBLISHED));
    }
}
