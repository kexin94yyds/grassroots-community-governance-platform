package com.cunzhi.governance.attachment.service;

import com.cunzhi.governance.attachment.mapper.EventAttachmentMapper;
import com.cunzhi.governance.task.mapper.TaskAttachmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists completed physical-file cleanup in an independent transaction. */
@Service
public class AttachmentPurgeMarkerService {

    private final EventAttachmentMapper eventAttachmentMapper;
    private final TaskAttachmentMapper taskAttachmentMapper;

    public AttachmentPurgeMarkerService(
            EventAttachmentMapper eventAttachmentMapper,
            TaskAttachmentMapper taskAttachmentMapper
    ) {
        this.eventAttachmentMapper = eventAttachmentMapper;
        this.taskAttachmentMapper = taskAttachmentMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markEventFilePurged(long attachmentId) {
        return eventAttachmentMapper.markFilePurged(attachmentId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markTaskFilePurged(long attachmentId) {
        return taskAttachmentMapper.markFilePurged(attachmentId);
    }
}
