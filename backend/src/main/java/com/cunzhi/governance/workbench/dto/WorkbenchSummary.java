package com.cunzhi.governance.workbench.dto;

import java.util.List;
import java.util.Map;

public record WorkbenchSummary(
        String role,
        String scopeLabel,
        Map<String, Long> metrics,
        List<WorkbenchItem> focusItems,
        List<WorkbenchItem> recentItems
) {
    public WorkbenchSummary {
        metrics = Map.copyOf(metrics);
        focusItems = List.copyOf(focusItems);
        recentItems = List.copyOf(recentItems);
    }
}
