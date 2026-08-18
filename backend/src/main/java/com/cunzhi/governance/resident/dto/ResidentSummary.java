package com.cunzhi.governance.resident.dto;

import java.time.LocalDate;
import java.util.List;

public record ResidentSummary(
        String id,
        String residentNo,
        String gridId,
        String gridName,
        String householdId,
        String householdNo,
        String realName,
        String gender,
        LocalDate birthDate,
        String idCardMasked,
        String phoneMasked,
        String address,
        boolean isHouseholder,
        List<String> specialGroupTags,
        String remark,
        String status,
        int version
) {
    public ResidentSummary {
        specialGroupTags = List.copyOf(specialGroupTags);
    }
}
