package com.cunzhi.governance.system.dto;

public record MenuItem(
        String id,
        String parentId,
        String code,
        String name,
        String type,
        String routePath,
        String permissionCode,
        String icon,
        int sortNo,
        String status,
        int version
) {
}
