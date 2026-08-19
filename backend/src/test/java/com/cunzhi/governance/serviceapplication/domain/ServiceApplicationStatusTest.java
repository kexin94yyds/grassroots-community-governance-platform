package com.cunzhi.governance.serviceapplication.domain;

import com.cunzhi.governance.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceApplicationStatusTest {

    @Test
    void permitsServiceApplicationHappyPathAndResidentCancellation() {
        assertDoesNotThrow(() -> ServiceApplicationStatus.SUBMITTED.requireTransitionTo(ServiceApplicationStatus.ACCEPTED));
        assertDoesNotThrow(() -> ServiceApplicationStatus.ACCEPTED.requireTransitionTo(ServiceApplicationStatus.PROCESSING));
        assertDoesNotThrow(() -> ServiceApplicationStatus.PROCESSING.requireTransitionTo(ServiceApplicationStatus.COMPLETED));
        assertDoesNotThrow(() -> ServiceApplicationStatus.SUBMITTED.requireTransitionTo(ServiceApplicationStatus.CANCELLED));
    }

    @Test
    void rejectsSkippingRequiredHandlingSteps() {
        assertThrows(BusinessException.class,
                () -> ServiceApplicationStatus.SUBMITTED.requireTransitionTo(ServiceApplicationStatus.COMPLETED));
        assertThrows(BusinessException.class,
                () -> ServiceApplicationStatus.COMPLETED.requireTransitionTo(ServiceApplicationStatus.PROCESSING));
    }
}
