package com.cunzhi.governance.system.dto;

import java.util.List;

public record RoleOption(
        String code,
        String name,
        String description,
        String status,
        List<String> menuIds,
        int version
) {
    public RoleOption {
        menuIds = menuIds == null ? List.of() : List.copyOf(menuIds);
    }
}
