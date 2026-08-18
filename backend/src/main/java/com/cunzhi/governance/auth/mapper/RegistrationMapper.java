package com.cunzhi.governance.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RegistrationMapper {

    @Select("select count(*) from sys_user where username = #{username}")
    int countByUsername(@Param("username") String username);

    @Select("""
            select id
            from resident
            where real_name = #{realName}
              and id_card_hash = #{idCardHash}
              and phone_hash = #{phoneHash}
              and status = 'ACTIVE'
              and user_id is null
            limit 1
            """)
    Long findAvailableResidentId(
            @Param("realName") String realName,
            @Param("idCardHash") String idCardHash,
            @Param("phoneHash") String phoneHash
    );

    @Select("select count(*) from sys_user where requested_resident_id = #{residentId}")
    int countRequestsForResident(@Param("residentId") long residentId);

    @Insert("""
            insert into sys_user
              (username, password_hash, real_name, phone, account_type, approval_status,
               requested_resident_id, registration_note, status)
            values
              (#{username}, #{passwordHash}, #{realName}, #{phone}, #{accountType}, 'PENDING',
               #{requestedResidentId}, #{note}, 'DISABLED')
            """)
    int insertPendingUser(
            @Param("username") String username,
            @Param("passwordHash") String passwordHash,
            @Param("realName") String realName,
            @Param("phone") String phone,
            @Param("accountType") String accountType,
            @Param("requestedResidentId") Long requestedResidentId,
            @Param("note") String note
    );
}

