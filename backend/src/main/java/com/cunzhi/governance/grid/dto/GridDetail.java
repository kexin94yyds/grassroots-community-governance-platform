package com.cunzhi.governance.grid.dto;

import java.math.BigDecimal;
import java.util.List;

public record GridDetail(
        String id,
        String communityId,
        String areaCode,
        String areaName,
        String areaType,
        String address,
        BigDecimal centerLongitude,
        BigDecimal centerLatitude,
        String boundaryGeojson,
        String status,
        int version,
        List<GridAssignmentView> assignments
) {
    public GridDetail {
        assignments = List.copyOf(assignments);
    }
}
