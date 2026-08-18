package com.cunzhi.governance.event.domain;

import com.cunzhi.governance.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventStatusTest {

    @Test
    void acceptsDocumentedTransitions() {
        assertDoesNotThrow(() -> EventStatus.REPORTED.requireTransitionTo(EventStatus.ACCEPTED));
        assertDoesNotThrow(() -> EventStatus.ACCEPTED.requireTransitionTo(EventStatus.ASSIGNED));
        assertDoesNotThrow(() -> EventStatus.PENDING_REVIEW.requireTransitionTo(EventStatus.CLOSED));
        assertDoesNotThrow(() -> EventStatus.PENDING_REVIEW.requireTransitionTo(EventStatus.PROCESSING));
    }

    @Test
    void rejectsSkippedOrTerminalTransitions() {
        assertThrows(
                BusinessException.class,
                () -> EventStatus.REPORTED.requireTransitionTo(EventStatus.PROCESSING)
        );
        assertThrows(
                BusinessException.class,
                () -> EventStatus.CLOSED.requireTransitionTo(EventStatus.PROCESSING)
        );
    }
}
