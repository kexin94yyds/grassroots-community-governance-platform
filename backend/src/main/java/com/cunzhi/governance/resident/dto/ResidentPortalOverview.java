package com.cunzhi.governance.resident.dto;

import com.cunzhi.governance.event.dto.EventCategoryOption;
import com.cunzhi.governance.event.dto.EventSummary;

import java.util.List;

public record ResidentPortalOverview(
        ResidentSummary profile,
        List<EventCategoryOption> categories,
        List<EventSummary> events
) {
    public ResidentPortalOverview {
        categories = List.copyOf(categories);
        events = List.copyOf(events);
    }
}

