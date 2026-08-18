package com.cunzhi.governance.grid.dto;

public record GridAssignmentView(
        String userId,
        String username,
        String realName,
        boolean primary
) {
}
