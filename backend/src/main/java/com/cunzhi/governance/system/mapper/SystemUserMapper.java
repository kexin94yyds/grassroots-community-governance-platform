package com.cunzhi.governance.system.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface SystemUserMapper {

    @Select("""
            <script>
            select count(*)
            from sys_user u
            <where>
              <if test="keyword != null and keyword != ''">
                (u.username like concat('%', #{keyword}, '%')
                 or u.real_name like concat('%', #{keyword}, '%'))
              </if>
            </where>
            </script>
            """)
    long count(@Param("keyword") String keyword);

    @Select("""
            <script>
            select u.id, u.username, u.real_name as realName, u.status,
                   u.account_type as accountType, u.approval_status as approvalStatus,
                   u.requested_resident_id as requestedResidentId,
                   requested.real_name as requestedResidentName,
                   u.version,
                   u.last_login_at as lastLoginAt,
                   group_concat(distinct r.role_code order by r.role_code separator ',') as roleCodes
            from sys_user u
            left join sys_user_role ur on ur.user_id = u.id and ur.status = 'ACTIVE'
            left join sys_role r on r.id = ur.role_id
            left join resident requested on requested.id = u.requested_resident_id
            <where>
              <if test="keyword != null and keyword != ''">
                (u.username like concat('%', #{keyword}, '%')
                 or u.real_name like concat('%', #{keyword}, '%'))
              </if>
            </where>
            group by u.id, u.username, u.real_name, u.status, u.account_type,
                     u.approval_status, u.requested_resident_id, requested.real_name,
                     u.version, u.last_login_at
            order by case u.approval_status when 'PENDING' then 0 else 1 end, u.id desc
            limit #{size} offset #{offset}
            </script>
            """)
    List<SystemUserRow> findPage(
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            select u.id, u.username, u.real_name as realName, u.phone, u.status,
                   u.account_type as accountType, u.approval_status as approvalStatus,
                   u.requested_resident_id as requestedResidentId,
                   requested.real_name as requestedResidentName,
                   u.registration_note as registrationNote,
                   u.rejection_reason as rejectionReason, u.reviewed_at as reviewedAt,
                   u.version,
                   u.last_login_at as lastLoginAt,
                   group_concat(distinct r.role_code order by r.role_code separator ',') as roleCodes
            from sys_user u
            left join sys_user_role ur on ur.user_id = u.id and ur.status = 'ACTIVE'
            left join sys_role r on r.id = ur.role_id
            left join resident requested on requested.id = u.requested_resident_id
            where u.id = #{id}
            group by u.id, u.username, u.real_name, u.phone, u.status, u.account_type,
                     u.approval_status, u.requested_resident_id, requested.real_name,
                     u.registration_note, u.rejection_reason, u.reviewed_at,
                     u.version, u.last_login_at
            """)
    SystemUserDetailRow findById(@Param("id") long id);

    @Select("select count(*) from sys_user where username = #{username}")
    int countByUsername(@Param("username") String username);

    @Insert("""
            insert into sys_user (username, password_hash, real_name, phone, status)
            values (#{username}, #{passwordHash}, #{realName}, #{phone}, 'ENABLED')
            """)
    int insertUser(
            @Param("username") String username,
            @Param("passwordHash") String passwordHash,
            @Param("realName") String realName,
            @Param("phone") String phone
    );

    @Select("select id from sys_user where username = #{username}")
    Long findIdByUsername(@Param("username") String username);

    @Update("""
            update sys_user
            set real_name = #{realName}, phone = #{phone}, version = version + 1
            where id = #{id} and version = #{version}
            """)
    int updateProfile(
            @Param("id") long id,
            @Param("realName") String realName,
            @Param("phone") String phone,
            @Param("version") int version
    );

    @Update("""
            update sys_user
            set status = #{status}, version = version + 1,
                security_version = security_version + 1
            where id = #{id} and version = #{version}
            """)
    int updateStatus(
            @Param("id") long id,
            @Param("status") String status,
            @Param("version") int version
    );

    @Update("""
            update sys_user
            set status = 'DISABLED', version = version + 1,
                security_version = security_version + 1
            where id = #{userId}
            """)
    int disableLinkedResidentUser(@Param("userId") long userId);

    @Update("""
            update sys_user
            set password_hash = #{passwordHash},
                password_change_required = 1,
                version = version + 1,
                security_version = security_version + 1
            where id = #{userId} and version = #{version}
            """)
    int resetPassword(
            @Param("userId") long userId,
            @Param("passwordHash") String passwordHash,
            @Param("version") int version
    );

    @Select("""
            select count(*) from resident
            where user_id = #{userId} and status = 'ACTIVE'
            """)
    int countActiveLinkedResident(@Param("userId") long userId);

    @Update("""
            update sys_user
            set approval_status = #{approvalStatus},
                status = #{status},
                reviewed_by = #{reviewerId},
                reviewed_at = current_timestamp(3),
                rejection_reason = #{rejectionReason},
                requested_resident_id = case
                  when #{approvalStatus} = 'REJECTED' then null
                  else requested_resident_id
                end,
                version = version + 1,
                security_version = security_version + 1
            where id = #{id}
              and approval_status = 'PENDING'
              and version = #{version}
            """)
    int reviewRegistration(
            @Param("id") long id,
            @Param("approvalStatus") String approvalStatus,
            @Param("status") String status,
            @Param("reviewerId") long reviewerId,
            @Param("rejectionReason") String rejectionReason,
            @Param("version") int version
    );

    @Select("""
            select count(*) from resident
            where id = #{residentId} and status = 'ACTIVE' and user_id is null
            """)
    int countAvailableResident(@Param("residentId") long residentId);

    @Update("""
            update resident
            set user_id = #{userId}, version = version + 1
            where id = #{residentId} and status = 'ACTIVE' and user_id is null
            """)
    int linkResidentUser(
            @Param("residentId") long residentId,
            @Param("userId") long userId
    );

    @Update("""
            update sys_user
            set version = version + 1, security_version = security_version + 1
            where id = #{id} and version = #{version}
            """)
    int touchVersion(@Param("id") long id, @Param("version") int version);

    @Select("""
            <script>
            select count(*) from sys_role
            where status = 'ENABLED' and role_code in
            <foreach collection="roleCodes" item="roleCode" open="(" separator="," close=")">
              #{roleCode}
            </foreach>
            </script>
            """)
    int countEnabledRoles(@Param("roleCodes") List<String> roleCodes);

    @Select("""
            select r.role_code
            from sys_user_role ur
            join sys_role r on r.id = ur.role_id
            where ur.user_id = #{userId}
              and ur.status = 'ACTIVE'
            order by r.role_code
            """)
    List<String> findRoleCodes(@Param("userId") long userId);

    @Insert("""
            insert into sys_user_role (user_id, role_id, assigned_at, status, ended_at)
            select #{userId}, id, current_timestamp(3), 'ACTIVE', null
            from sys_role
            where role_code = #{roleCode} and status = 'ENABLED'
            on duplicate key update
              assigned_at = current_timestamp(3), status = 'ACTIVE', ended_at = null
            """)
    int insertUserRole(@Param("userId") long userId, @Param("roleCode") String roleCode);

    @Update("""
            update sys_user_role
            set status = 'ENDED', ended_at = current_timestamp(3)
            where user_id = #{userId} and status = 'ACTIVE'
            """)
    int endUserRoles(@Param("userId") long userId);

    @Select("""
            select count(*)
            from sys_user u
            join sys_user_role ur on ur.user_id = u.id
            join sys_role r on r.id = ur.role_id
            where r.role_code = 'SYSTEM_ADMIN'
              and ur.status = 'ACTIVE'
              and u.status = 'ENABLED'
              and u.id <> #{excludedUserId}
            """)
    int countOtherEnabledSystemAdmins(@Param("excludedUserId") long excludedUserId);

    @Select("""
            select count(*)
            from user_area_assignment
            where user_id = #{userId}
              and assignment_type = #{assignmentType}
              and status = 'ACTIVE'
            """)
    int countActiveAssignments(
            @Param("userId") long userId,
            @Param("assignmentType") String assignmentType
    );

    @Select("""
            select count(*)
            from work_task
            where assignee_user_id = #{userId}
              and status not in ('COMPLETED', 'CANCELLED')
            """)
    int countOpenAssignedTasks(@Param("userId") long userId);

    @Select("""
            select role_code as code, role_name as name, description, status
            from sys_role
            order by role_code
            """)
    List<RoleRow> findRoles();

    @Select("""
            select id, parent_id as parentId, menu_code as code, menu_name as name,
                   menu_type as type, route_path as routePath, permission_code as permissionCode,
                   icon, sort_no as sortNo, status
            from sys_menu
            order by sort_no, id
            """)
    List<MenuRow> findMenus();

    record SystemUserRow(
            Long id,
            String username,
            String realName,
            String status,
            String accountType,
            String approvalStatus,
            Long requestedResidentId,
            String requestedResidentName,
            int version,
            LocalDateTime lastLoginAt,
            String roleCodes
    ) {
    }

    record SystemUserDetailRow(
            Long id,
            String username,
            String realName,
            String phone,
            String status,
            String accountType,
            String approvalStatus,
            Long requestedResidentId,
            String requestedResidentName,
            String registrationNote,
            String rejectionReason,
            LocalDateTime reviewedAt,
            int version,
            LocalDateTime lastLoginAt,
            String roleCodes
    ) {
    }

    record RoleRow(String code, String name, String description, String status) {
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
            String status
    ) {
    }
}
