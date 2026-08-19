package com.cunzhi.governance.announcement.dto;

import java.time.LocalDateTime;

public record AnnouncementView(
        String id,
        String announcementNo,
        String audienceScope,
        String communityId,
        String communityName,
        String title,
        String content,
        boolean pinned,
        String status,
        String createdBy,
        String createdByName,
        LocalDateTime publishedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt,
        int version
) {
}
