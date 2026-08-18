package com.cunzhi.governance.auth.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface UserAuthMapper {

    @Select("""
            select id, username, password_hash as passwordHash, real_name as realName, status,
                   security_version as securityVersion,
                   password_change_required as passwordChangeRequired
            from sys_user
            where username = #{username}
            limit 1
            """)
    Optional<UserAccountRow> findByUsername(@Param("username") String username);

    @Select("""
            select r.role_code
            from sys_role r
            join sys_user_role ur on ur.role_id = r.id
            where ur.user_id = #{userId}
              and ur.status = 'ACTIVE'
              and r.status = 'ENABLED'
            order by r.role_code
            """)
    List<String> findRoleCodes(@Param("userId") long userId);

    @Select("""
            select distinct m.permission_code
            from sys_menu m
            join sys_role_menu rm on rm.menu_id = m.id
            join sys_user_role ur on ur.role_id = rm.role_id
            join sys_role r on r.id = ur.role_id
            where ur.user_id = #{userId}
              and ur.status = 'ACTIVE'
              and r.status = 'ENABLED'
              and m.status = 'ENABLED'
              and m.permission_code is not null
              and m.permission_code <> ''
            order by m.permission_code
            """)
    List<String> findPermissionCodes(@Param("userId") long userId);

    @Select("""
            select distinct m.id, m.menu_code as code, m.menu_name as name,
                   m.route_path as routePath, m.icon, m.sort_no as sortNo
            from sys_menu m
            join sys_role_menu rm on rm.menu_id = m.id
            join sys_user_role ur on ur.role_id = rm.role_id
            join sys_role r on r.id = ur.role_id
            where ur.user_id = #{userId}
              and ur.status = 'ACTIVE'
              and r.status = 'ENABLED'
              and m.status = 'ENABLED'
              and m.menu_type = 'MENU'
            order by m.sort_no, m.id
            """)
    List<NavigationRow> findEnabledNavigationMenus(@Param("userId") long userId);

    @Update("""
            update sys_user
            set last_login_at = current_timestamp(3)
            where id = #{userId}
            """)
    int updateLastLoginAt(@Param("userId") long userId);

    @Update("""
            update sys_user
            set password_hash = #{passwordHash},
                password_change_required = 0,
                version = version + 1,
                security_version = security_version + 1
            where id = #{userId} and security_version = #{securityVersion}
            """)
    int updateOwnPassword(
            @Param("userId") long userId,
            @Param("passwordHash") String passwordHash,
            @Param("securityVersion") long securityVersion
    );

    @Select("""
            select status, security_version as securityVersion,
                   password_change_required as passwordChangeRequired
            from sys_user where id = #{userId}
            """)
    SessionStateRow findSessionState(@Param("userId") long userId);

    record UserAccountRow(
            Long id,
            String username,
            String passwordHash,
            String realName,
            String status,
            long securityVersion,
            boolean passwordChangeRequired
    ) {
    }

    record SessionStateRow(String status, long securityVersion, boolean passwordChangeRequired) {
    }

    record NavigationRow(Long id, String code, String name, String routePath, String icon, int sortNo) {
    }
}
