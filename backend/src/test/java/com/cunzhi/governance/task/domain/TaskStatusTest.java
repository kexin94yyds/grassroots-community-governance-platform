package com.cunzhi.governance.task.domain;

import com.cunzhi.governance.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskStatusTest {

    @Test
    void acceptsDocumentedTransitions() {
        assertDoesNotThrow(() -> TaskStatus.PENDING_ACCEPT.requireTransitionTo(TaskStatus.PROCESSING));
        assertDoesNotThrow(() -> TaskStatus.PROCESSING.requireTransitionTo(TaskStatus.PENDING_REVIEW));
        assertDoesNotThrow(() -> TaskStatus.PENDING_REVIEW.requireTransitionTo(TaskStatus.COMPLETED));
        assertDoesNotThrow(() -> TaskStatus.PENDING_REVIEW.requireTransitionTo(TaskStatus.PROCESSING));
    }

    @Test
    void rejectsSkippedOrTerminalTransitions() {
        assertThrows(
                BusinessException.class,
                () -> TaskStatus.PENDING_ACCEPT.requireTransitionTo(TaskStatus.COMPLETED)
        );
        assertThrows(
                BusinessException.class,
                () -> TaskStatus.COMPLETED.requireTransitionTo(TaskStatus.PROCESSING)
        );
    }
}
