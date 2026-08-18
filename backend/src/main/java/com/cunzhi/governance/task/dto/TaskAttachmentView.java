package com.cunzhi.governance.task.dto;

import java.time.LocalDateTime;

public record TaskAttachmentView(
        String id,
        String taskId,
        String originalName,
        String contentType,
        long fileSize,
        String sha256,
        String uploadedBy,
        String uploaderName,
        LocalDateTime createdAt
) {
}
