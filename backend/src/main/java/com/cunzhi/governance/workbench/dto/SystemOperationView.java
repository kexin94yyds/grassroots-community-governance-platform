package com.cunzhi.governance.workbench.dto;

import java.time.LocalDateTime;

public record SystemOperationView(
        String id,
        LocalDateTime createdAt,
        String module,
        String moduleLabel,
        String action,
        String actionLabel,
        String operatorName,
        String objectLabel,
        String scopeLabel,
        String result,
        String resultLabel
) {
}
