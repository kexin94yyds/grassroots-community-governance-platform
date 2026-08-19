package com.cunzhi.governance.serviceapplication.dto;

public record ServiceCatalogView(
        String id,
        String code,
        String name,
        String description,
        int sortNo,
        String status,
        int version
) {
}
