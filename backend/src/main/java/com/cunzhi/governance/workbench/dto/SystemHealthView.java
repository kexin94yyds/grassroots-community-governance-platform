package com.cunzhi.governance.workbench.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record SystemHealthView(
        LocalDateTime databaseTime,
        String flywayVersion,
        long accountCount,
        String businessConsistency,
        Map<String, Long> counts,
        Map<String, Long> consistencyCounts
) {
    public SystemHealthView {
        counts = Map.copyOf(counts);
        consistencyCounts = Map.copyOf(consistencyCounts);
    }
}
