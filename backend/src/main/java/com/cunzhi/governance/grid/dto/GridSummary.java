package com.cunzhi.governance.grid.dto;

public record GridSummary(
        String id,
        String communityId,
        String communityName,
        String areaCode,
        String areaName,
        String areaType,
        String address,
        String status,
        int version
) {
}
