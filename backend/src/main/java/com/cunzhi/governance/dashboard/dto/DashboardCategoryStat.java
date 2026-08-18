package com.cunzhi.governance.dashboard.dto;

import java.math.BigDecimal;

public record DashboardCategoryStat(
        String categoryId,
        String categoryName,
        long eventCount,
        BigDecimal percentage
) {
}
