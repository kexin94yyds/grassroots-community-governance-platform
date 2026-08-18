package com.cunzhi.governance.event.dto;

public record EventCategoryView(
        String id,
        String code,
        String name,
        String description,
        int sortNo,
        String status,
        int version
) {
}
