package com.cunzhi.governance.auth.dto;

import com.cunzhi.governance.auth.model.AuthenticatedUser;

import java.util.Set;

public record CurrentUserResponse(
        String id,
        String username,
        String realName,
        boolean passwordChangeRequired,
        Set<String> roles,
        Set<String> permissions
) {
    public static CurrentUserResponse from(AuthenticatedUser user) {
        return new CurrentUserResponse(
                user.id().toString(),
                user.getUsername(),
                user.realName(),
                user.passwordChangeRequired(),
                user.roles(),
                user.permissions()
        );
    }
}
