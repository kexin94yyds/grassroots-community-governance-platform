package com.cunzhi.governance.auth.dto;

public record NavigationItem(
        String id,
        String code,
        String name,
        String routePath,
        String icon,
        int sortNo
) {
}
