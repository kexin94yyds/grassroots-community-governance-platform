package com.cunzhi.governance.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SystemAccessMapper {

    @Select("""
            select r.role_code as code, r.role_name as name, r.description, r.status, r.version,
                   group_concat(rm.menu_id order by rm.menu_id separator ',') as menuIds
            from sys_role r
            left join sys_role_menu rm on rm.role_id = r.id
            group by r.id, r.role_code, r.role_name, r.description, r.status, r.version
            order by r.role_code
            """)
    List<RoleRow> findRoles();

    @Select("""
            select r.role_code as code, r.role_name as name, r.description, r.status, r.version,
                   group_concat(rm.menu_id order by rm.menu_id separator ',') as menuIds
            from sys_role r
            left join sys_role_menu rm on rm.role_id = r.id
            where r.role_code = #{code}
            group by r.id, r.role_code, r.role_name, r.description, r.status, r.version
            """)
    RoleRow findRole(@Param("code") String code);

    @Update("""
            update sys_role
            set role_name = #{name}, description = #{description}, status = #{status}, version = version + 1
            where role_code = #{code} and version = #{version}
            """)
    int updateRole(
            @Param("code") String code,
            @Param("name") String name,
            @Param("description") String description,
            @Param("status") String status,
            @Param("version") int version
    );

    @Delete("""
            delete rm from sys_role_menu rm
            join sys_role r on r.id = rm.role_id
            where r.role_code = #{code}
            """)
    int deleteRoleMenus(@Param("code") String code);

    @Insert("""
            insert into sys_role_menu (role_id, menu_id, assigned_at)
            select r.id, #{menuId}, current_timestamp(3)
            from sys_role r
            where r.role_code = #{code}
            """)
    int insertRoleMenu(@Param("code") String code, @Param("menuId") long menuId);

    @Select("""
            select count(distinct u.id)
            from sys_user u
            join sys_user_role ur on ur.user_id = u.id and ur.status = 'ACTIVE'
            join sys_role r on r.id = ur.role_id
            where r.role_code = #{code} and u.status = 'ENABLED'
            """)
    int countActiveUsersForRole(@Param("code") String code);

    @Update("""
            update sys_user u
            join (
              select distinct ur.user_id
              from sys_user_role ur
              join sys_role r on r.id = ur.role_id
              where r.role_code = #{code} and ur.status = 'ACTIVE'
            ) affected on affected.user_id = u.id
            set u.security_version = u.security_version + 1
            """)
    int bumpUserSecurityVersionsForRole(@Param("code") String code);

    @Select("""
            select id, parent_id as parentId, menu_code as code, menu_name as name,
                   menu_type as type, route_path as routePath, permission_code as permissionCode,
                   icon, sort_no as sortNo, status, version
            from sys_menu
            order by sort_no, id
            """)
    List<MenuRow> findMenus();

    @Select("""
            select id, parent_id as parentId, menu_code as code, menu_name as name,
                   menu_type as type, route_path as routePath, permission_code as permissionCode,
                   icon, sort_no as sortNo, status, version
            from sys_menu where id = #{id}
            """)
    MenuRow findMenu(@Param("id") long id);

    @Select("""
            <script>
            select count(*) from sys_menu
            where status = 'ENABLED' and id in
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int countEnabledMenus(@Param("ids") List<Long> ids);

    @Select("""
            <script>
            select count(*)
            from sys_menu child
            where child.id in
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
              and child.parent_id is not null
              and child.parent_id not in
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int countSelectedMenusWithMissingParents(@Param("ids") List<Long> ids);

    @Select("""
            select count(*) from sys_menu
            where parent_id = #{id} and status = 'ENABLED'
            """)
    int countEnabledChildren(@Param("id") long id);

    @Update("""
            update sys_menu
            set menu_name = #{name}, icon = #{icon}, sort_no = #{sortNo},
                status = #{status}, version = version + 1
            where id = #{id} and version = #{version}
            """)
    int updateMenu(
            @Param("id") long id,
            @Param("name") String name,
            @Param("icon") String icon,
            @Param("sortNo") int sortNo,
            @Param("status") String status,
            @Param("version") int version
    );

    @Update("""
            update sys_user u
            join (
              select distinct ur.user_id
              from sys_user_role ur
              join sys_role_menu rm on rm.role_id = ur.role_id
              where rm.menu_id = #{menuId} and ur.status = 'ACTIVE'
            ) affected on affected.user_id = u.id
            set u.security_version = u.security_version + 1
            """)
    int bumpUserSecurityVersionsForMenu(@Param("menuId") long menuId);

    record RoleRow(
            String code,
            String name,
            String description,
            String status,
            int version,
            String menuIds
    ) {
    }

    record MenuRow(
            Long id,
            Long parentId,
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
}
