package com.cunzhi.governance.attachment.dto;

import java.time.LocalDateTime;

public record EventAttachmentView(
        String id,
        String eventId,
        String originalName,
        String contentType,
        long fileSize,
        String sha256,
        String uploadedBy,
        String uploaderName,
        LocalDateTime createdAt
) {
}
