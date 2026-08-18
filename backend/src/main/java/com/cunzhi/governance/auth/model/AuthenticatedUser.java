package com.cunzhi.governance.auth.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AuthenticatedUser implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String realName;
    private final boolean enabled;
    private final long securityVersion;
    private final boolean passwordChangeRequired;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Set<GrantedAuthority> authorities;

    public AuthenticatedUser(
            Long id,
            String username,
            String passwordHash,
            String realName,
            boolean enabled,
            Set<String> roles,
            Set<String> permissions
    ) {
        this(id, username, passwordHash, realName, enabled, 0L, false, roles, permissions);
    }

    public AuthenticatedUser(
            Long id,
            String username,
            String passwordHash,
            String realName,
            boolean enabled,
            long securityVersion,
            Set<String> roles,
            Set<String> permissions
    ) {
        this(id, username, passwordHash, realName, enabled, securityVersion, false, roles, permissions);
    }

    public AuthenticatedUser(
            Long id,
            String username,
            String passwordHash,
            String realName,
            boolean enabled,
            long securityVersion,
            boolean passwordChangeRequired,
            Set<String> roles,
            Set<String> permissions
    ) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.realName = realName;
        this.enabled = enabled;
        this.securityVersion = securityVersion;
        this.passwordChangeRequired = passwordChangeRequired;
        this.roles = Set.copyOf(roles);
        this.permissions = Set.copyOf(permissions);

        Set<GrantedAuthority> resolvedAuthorities = new LinkedHashSet<>();
        roles.forEach(role -> resolvedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        permissions.forEach(permission -> resolvedAuthorities.add(new SimpleGrantedAuthority(permission)));
        this.authorities = Set.copyOf(resolvedAuthorities);
    }

    public Long id() {
        return id;
    }

    public String realName() {
        return realName;
    }

    public Set<String> roles() {
        return roles;
    }

    public Set<String> permissions() {
        return permissions;
    }

    public long securityVersion() {
        return securityVersion;
    }

    public boolean passwordChangeRequired() {
        return passwordChangeRequired;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
