package com.cunzhi.governance.resident.dto;

public record HouseholdSummary(
        String id,
        String householdNo,
        String gridId,
        String gridName,
        String buildingNo,
        String unitNo,
        String roomNo,
        String address,
        String status,
        int version
) {
}
