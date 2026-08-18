package com.cunzhi.governance.resident.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface ResidentSensitiveAccessMapper {

    @Insert("""
            insert into resident_sensitive_access_log
              (operator_user_id, resident_id, scope_grid_id, action, field_type, purpose, result_count)
            values
              (#{operatorUserId}, #{residentId}, #{scopeGridId}, #{action}, #{fieldType}, #{purpose}, #{resultCount})
            """)
    int insert(
            @Param("operatorUserId") long operatorUserId,
            @Param("residentId") Long residentId,
            @Param("scopeGridId") Long scopeGridId,
            @Param("action") String action,
            @Param("fieldType") String fieldType,
            @Param("purpose") String purpose,
            @Param("resultCount") int resultCount
    );

    @Select("""
            <script>
            select count(*)
            from resident_sensitive_access_log l
            join sys_user operator_user on operator_user.id = l.operator_user_id
            left join resident r on r.id = l.resident_id
            left join grid_area scope_grid on scope_grid.id = l.scope_grid_id
            where 1 = 1
              <if test="action != null and action != ''">and l.action = #{action}</if>
              <if test="fieldType != null and fieldType != ''">and l.field_type = #{fieldType}</if>
              <if test="keyword != null and keyword != ''">
                and (operator_user.username like concat('%', #{keyword}, '%')
                     or operator_user.real_name like concat('%', #{keyword}, '%')
                     or r.resident_no like concat('%', #{keyword}, '%')
                     or l.purpose like concat('%', #{keyword}, '%'))
              </if>
              <if test="!allAccess">
                and (
                  <choose>
                    <when test="gridIds != null and gridIds.size() > 0">
                      l.scope_grid_id in
                      <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
                    </when>
                    <otherwise>1 = 0</otherwise>
                  </choose>
                  or (l.scope_grid_id is null and l.operator_user_id = #{currentUserId})
                )
              </if>
            </script>
            """)
    long countPage(
            @Param("action") String action,
            @Param("fieldType") String fieldType,
            @Param("keyword") String keyword,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds,
            @Param("currentUserId") long currentUserId
    );

    @Select("""
            <script>
            select l.id, l.operator_user_id as operatorUserId,
                   coalesce(operator_user.real_name, operator_user.username) as operatorName,
                   operator_user.username as operatorUsername,
                   r.id as residentId, r.resident_no as residentNo, r.real_name as residentName,
                   scope_grid.id as scopeGridId, scope_grid.area_code as scopeGridCode,
                   scope_grid.area_name as scopeGridName,
                   l.action, l.field_type as fieldType, l.purpose, l.result_count as resultCount,
                   l.created_at as createdAt
            from resident_sensitive_access_log l
            join sys_user operator_user on operator_user.id = l.operator_user_id
            left join resident r on r.id = l.resident_id
            left join grid_area scope_grid on scope_grid.id = l.scope_grid_id
            where 1 = 1
              <if test="action != null and action != ''">and l.action = #{action}</if>
              <if test="fieldType != null and fieldType != ''">and l.field_type = #{fieldType}</if>
              <if test="keyword != null and keyword != ''">
                and (operator_user.username like concat('%', #{keyword}, '%')
                     or operator_user.real_name like concat('%', #{keyword}, '%')
                     or r.resident_no like concat('%', #{keyword}, '%')
                     or l.purpose like concat('%', #{keyword}, '%'))
              </if>
              <if test="!allAccess">
                and (
                  <choose>
                    <when test="gridIds != null and gridIds.size() > 0">
                      l.scope_grid_id in
                      <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
                    </when>
                    <otherwise>1 = 0</otherwise>
                  </choose>
                  or (l.scope_grid_id is null and l.operator_user_id = #{currentUserId})
                )
              </if>
            order by l.created_at desc, l.id desc
            limit #{size} offset #{offset}
            </script>
            """)
    List<AccessLogRow> findPage(
            @Param("action") String action,
            @Param("fieldType") String fieldType,
            @Param("keyword") String keyword,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds,
            @Param("currentUserId") long currentUserId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    record AccessLogRow(
            Long id, Long operatorUserId, String operatorName, String operatorUsername,
            Long residentId, String residentNo, String residentName,
            Long scopeGridId, String scopeGridCode, String scopeGridName,
            String action, String fieldType, String purpose, int resultCount, LocalDateTime createdAt
    ) {
    }
}
