package com.cunzhi.governance.attachment.service;

import com.cunzhi.governance.attachment.mapper.EventAttachmentMapper;
import com.cunzhi.governance.config.AppProperties;
import com.cunzhi.governance.task.mapper.TaskAttachmentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.LongConsumer;

/**
 * Durable cleanup for soft-deleted attachment files.
 *
 * <p>Deleting a file is deliberately done before the independent marker transaction. If the
 * process stops between those two operations, the pending database row remains and a later run
 * observes the missing file as a successful deletion before marking it complete.</p>
 */
@Service
public class AttachmentPurgeService {

    public static final int DEFAULT_BATCH_LIMIT = 100;

    private static final Logger log = LoggerFactory.getLogger(AttachmentPurgeService.class);

    private final EventAttachmentMapper eventAttachmentMapper;
    private final TaskAttachmentMapper taskAttachmentMapper;
    private final AttachmentPurgeMarkerService markerService;
    private final AttachmentFileStore fileStore;

    public AttachmentPurgeService(
            EventAttachmentMapper eventAttachmentMapper,
            TaskAttachmentMapper taskAttachmentMapper,
            AttachmentPurgeMarkerService markerService,
            AppProperties appProperties
    ) {
        this.eventAttachmentMapper = eventAttachmentMapper;
        this.taskAttachmentMapper = taskAttachmentMapper;
        this.markerService = markerService;
        this.fileStore = new AttachmentFileStore(appProperties.attachment());
    }

    public void purgeEventAttachment(long attachmentId, String storageKey) {
        purge("event", attachmentId, storageKey, id -> markerService.markEventFilePurged(id));
    }

    public void purgeTaskAttachment(long attachmentId, String storageKey) {
        purge("task", attachmentId, storageKey, id -> markerService.markTaskFilePurged(id));
    }

    /** Runs bounded, idempotent retries for records left pending by a crash or I/O failure. */
    public void retryPendingAttachments(int requestedBatchLimit) {
        int batchLimit = Math.max(1, Math.min(requestedBatchLimit, DEFAULT_BATCH_LIMIT));
        retryPendingEvents(batchLimit);
        retryPendingTasks(batchLimit);
    }

    private void retryPendingEvents(int batchLimit) {
        try {
            List<EventAttachmentMapper.PendingFilePurgeRow> pending = eventAttachmentMapper.findPendingFilePurges(batchLimit);
            if (pending != null) {
                pending.forEach(row -> purgeEventAttachment(row.id(), row.storageKey()));
            }
        } catch (RuntimeException exception) {
            log.error("Unable to scan pending event attachment file purges", exception);
        }
    }

    private void retryPendingTasks(int batchLimit) {
        try {
            List<TaskAttachmentMapper.PendingFilePurgeRow> pending = taskAttachmentMapper.findPendingFilePurges(batchLimit);
            if (pending != null) {
                pending.forEach(row -> purgeTaskAttachment(row.id(), row.storageKey()));
            }
        } catch (RuntimeException exception) {
            log.error("Unable to scan pending task attachment file purges", exception);
        }
    }

    private void purge(String kind, long attachmentId, String storageKey, LongConsumer marker) {
        try {
            fileStore.deleteStoredFileIfExists(storageKey);
        } catch (RuntimeException exception) {
            log.error("Failed to delete {} attachment file for pending attachment {}: {}",
                    kind, attachmentId, exception.getMessage());
            return;
        }
        try {
            marker.accept(attachmentId);
        } catch (RuntimeException exception) {
            log.error("{} attachment {} file was removed but its purge marker remains pending: {}",
                    kind, attachmentId, exception.getMessage());
        }
    }
}
