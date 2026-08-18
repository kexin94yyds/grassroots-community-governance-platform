package com.cunzhi.governance.auth.service;

import com.cunzhi.governance.auth.mapper.UserAuthMapper;
import com.cunzhi.governance.auth.model.AuthenticatedUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserAuthMapper userAuthMapper;

    public DatabaseUserDetailsService(UserAuthMapper userAuthMapper) {
        this.userAuthMapper = userAuthMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAuthMapper.UserAccountRow row = userAuthMapper.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new AuthenticatedUser(
                row.id(),
                row.username(),
                row.passwordHash(),
                row.realName(),
                "ENABLED".equals(row.status()),
                row.securityVersion(),
                row.passwordChangeRequired(),
                new LinkedHashSet<>(userAuthMapper.findRoleCodes(row.id())),
                new LinkedHashSet<>(userAuthMapper.findPermissionCodes(row.id()))
        );
    }
}
