package com.cunzhi.governance.attachment.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries durable attachment cleanup shortly after startup and at a fixed cadence. */
@Component
public class AttachmentPurgeScheduler {

    private final AttachmentPurgeService purgeService;

    public AttachmentPurgeScheduler(AttachmentPurgeService purgeService) {
        this.purgeService = purgeService;
    }

    @Scheduled(initialDelay = 10_000L, fixedDelay = 60_000L)
    public void retryPendingAttachmentFiles() {
        purgeService.retryPendingAttachments(AttachmentPurgeService.DEFAULT_BATCH_LIMIT);
    }
}
