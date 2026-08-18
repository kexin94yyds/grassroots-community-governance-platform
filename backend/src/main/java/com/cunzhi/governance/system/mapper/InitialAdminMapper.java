package com.cunzhi.governance.system.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InitialAdminMapper {

    @Select("select id from sys_role where role_code = 'SYSTEM_ADMIN' for update")
    Long lockSystemAdminRole();

    @Select("""
            select count(*)
            from sys_user u
            join sys_user_role ur on ur.user_id = u.id
            join sys_role r on r.id = ur.role_id
            where r.role_code = 'SYSTEM_ADMIN'
              and ur.status = 'ACTIVE'
            """)
    int countSystemAdminUsers();

    @Select("select id from sys_user where username = #{username}")
    Long findUserIdByUsername(@Param("username") String username);

    @Insert("""
            insert into sys_user (username, password_hash, real_name, status)
            values (#{username}, #{passwordHash}, #{realName}, 'ENABLED')
            """)
    int insertUser(
            @Param("username") String username,
            @Param("passwordHash") String passwordHash,
            @Param("realName") String realName
    );

    @Insert("insert into sys_user_role (user_id, role_id) values (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") long userId, @Param("roleId") long roleId);
}
