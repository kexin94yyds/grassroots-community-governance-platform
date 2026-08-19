package com.cunzhi.governance.workbench.dto;

import java.time.LocalDateTime;

public record WorkbenchItem(
        String type,
        String id,
        String title,
        String status,
        String route,
        LocalDateTime occurredAt
) {
}
