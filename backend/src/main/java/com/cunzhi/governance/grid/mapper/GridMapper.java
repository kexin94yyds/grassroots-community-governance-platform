package com.cunzhi.governance.grid.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

public interface GridMapper {

    @Select("""
            <script>
            select count(*)
            from grid_area grid
            where grid.area_type = #{areaType}
              <if test="keyword != null and keyword != ''">
                and (grid.area_code like concat('%', #{keyword}, '%')
                     or grid.area_name like concat('%', #{keyword}, '%')
                     or grid.address like concat('%', #{keyword}, '%'))
              </if>
              <if test="status != null and status != ''">and grid.status = #{status}</if>
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    <choose>
                      <when test="areaType == 'COMMUNITY'">
                        and grid.id in (
                          select distinct child.parent_id
                          from grid_area child
                          where child.id in
                          <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                            #{gridId}
                          </foreach>
                            and child.area_type = 'GRID'
                            and child.parent_id is not null
                        )
                      </when>
                      <otherwise>
                        and grid.id in
                        <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                          #{gridId}
                        </foreach>
                      </otherwise>
                    </choose>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            </script>
            """)
    long count(
            @Param("areaType") String areaType,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            <script>
            select grid.id, grid.parent_id as communityId,
                   community.area_name as communityName, grid.area_code as areaCode,
                   grid.area_name as areaName, grid.area_type as areaType,
                   grid.address, grid.status, grid.version
            from grid_area grid
            left join grid_area community on community.id = grid.parent_id
            where grid.area_type = #{areaType}
              <if test="keyword != null and keyword != ''">
                and (grid.area_code like concat('%', #{keyword}, '%')
                     or grid.area_name like concat('%', #{keyword}, '%')
                     or grid.address like concat('%', #{keyword}, '%'))
              </if>
              <if test="status != null and status != ''">and grid.status = #{status}</if>
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    <choose>
                      <when test="areaType == 'COMMUNITY'">
                        and grid.id in (
                          select distinct child.parent_id
                          from grid_area child
                          where child.id in
                          <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                            #{gridId}
                          </foreach>
                            and child.area_type = 'GRID'
                            and child.parent_id is not null
                        )
                      </when>
                      <otherwise>
                        and grid.id in
                        <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                          #{gridId}
                        </foreach>
                      </otherwise>
                    </choose>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            order by grid.area_code
            limit #{size} offset #{offset}
            </script>
            """)
    List<GridRow> findPage(
            @Param("areaType") String areaType,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            select id, parent_id as communityId, area_code as areaCode, area_name as areaName,
                   area_type as areaType, address, center_longitude as centerLongitude,
                   center_latitude as centerLatitude, cast(boundary_geojson as char) as boundaryGeojson,
                   status, version
            from grid_area where id = #{id}
            """)
    GridDetailRow findById(@Param("id") long id);

    @Select("select id from grid_area where id = #{id} for update")
    Long lockArea(@Param("id") long id);

    @Insert("""
            insert into grid_area
              (parent_id, area_code, area_name, area_type, address, center_longitude,
               center_latitude, boundary_geojson, status, created_by)
            values
              (#{communityId}, #{areaCode}, #{areaName}, #{areaType}, #{address},
               #{centerLongitude}, #{centerLatitude}, cast(#{boundaryGeojson} as json),
               'ENABLED', #{createdBy})
            """)
    int insertArea(
            @Param("communityId") Long communityId,
            @Param("areaCode") String areaCode,
            @Param("areaName") String areaName,
            @Param("areaType") String areaType,
            @Param("address") String address,
            @Param("centerLongitude") BigDecimal centerLongitude,
            @Param("centerLatitude") BigDecimal centerLatitude,
            @Param("boundaryGeojson") String boundaryGeojson,
            @Param("createdBy") long createdBy
    );

    @Select("select id from grid_area where area_code = #{areaCode}")
    Long findIdByAreaCode(@Param("areaCode") String areaCode);

    @Update("""
            update grid_area
            set area_name = #{areaName}, address = #{address},
                center_longitude = #{centerLongitude}, center_latitude = #{centerLatitude},
                boundary_geojson = cast(#{boundaryGeojson} as json),
                version = version + 1
            where id = #{id} and version = #{version}
            """)
    int updateArea(
            @Param("id") long id,
            @Param("areaName") String areaName,
            @Param("address") String address,
            @Param("centerLongitude") BigDecimal centerLongitude,
            @Param("centerLatitude") BigDecimal centerLatitude,
            @Param("boundaryGeojson") String boundaryGeojson,
            @Param("version") int version
    );

    @Update("""
            update grid_area set status = #{status}, version = version + 1
            where id = #{id} and version = #{version}
            """)
    int updateStatus(
            @Param("id") long id,
            @Param("status") String status,
            @Param("version") int version
    );

    @Update("""
            update grid_area set version = version + 1
            where id = #{id} and version = #{version}
            """)
    int touchVersion(@Param("id") long id, @Param("version") int version);

    @Select("""
            select count(*) from grid_area
            where id = #{communityId} and area_type = 'COMMUNITY' and status = 'ENABLED'
            """)
    int countEnabledCommunity(@Param("communityId") long communityId);

    @Select("""
            select count(*) from grid_area
            where parent_id = #{communityId} and area_type = 'GRID' and status = 'ENABLED'
            """)
    int countEnabledChildGrids(@Param("communityId") long communityId);

    @Select("""
            select count(*) from resident
            where grid_id = #{gridId} and status = 'ACTIVE'
            """)
    int countActiveResidents(@Param("gridId") long gridId);

    @Select("""
            select count(*) from governance_event
            where grid_id = #{gridId} and status not in ('CLOSED', 'REJECTED', 'CANCELLED')
            """)
    int countOpenEvents(@Param("gridId") long gridId);

    @Select("""
            select count(*) from work_task
            where grid_id = #{gridId} and status not in ('COMPLETED', 'CANCELLED')
            """)
    int countOpenTasks(@Param("gridId") long gridId);

    @Select("""
            select count(*) from service_application
            where grid_id = #{gridId} and status not in ('COMPLETED', 'REJECTED', 'CANCELLED')
            """)
    int countOpenServiceApplications(@Param("gridId") long gridId);

    @Select("""
            select count(*)
            from service_application application
            join grid_area grid on grid.id = application.grid_id
            where application.handler_user_id = #{userId}
              and grid.parent_id = #{communityId}
              and application.status not in ('COMPLETED', 'REJECTED', 'CANCELLED')
            """)
    int countOpenHandledServiceApplications(
            @Param("userId") long userId,
            @Param("communityId") long communityId
    );

    @Select("""
            select assignment.user_id as userId, u.username, u.real_name as realName,
                   assignment.is_primary as primaryFlag
            from user_area_assignment assignment
            join sys_user u on u.id = assignment.user_id
            where assignment.area_id = #{areaId}
              and assignment.assignment_type = #{assignmentType}
              and assignment.status = 'ACTIVE'
            order by assignment.is_primary desc, u.real_name
            """)
    List<AssignmentRow> findActiveAssignments(
            @Param("areaId") long areaId,
            @Param("assignmentType") String assignmentType
    );

    @Select("""
            select assignment.user_id as userId, u.username, u.real_name as realName,
                   assignment.is_primary as primaryFlag
            from user_area_assignment assignment
            join sys_user u on u.id = assignment.user_id
            where assignment.area_id = #{areaId}
              and assignment.assignment_type = #{assignmentType}
              and assignment.status = 'ACTIVE'
            order by assignment.is_primary desc, u.real_name
            for update
            """)
    List<AssignmentRow> findActiveAssignmentsForUpdate(
            @Param("areaId") long areaId,
            @Param("assignmentType") String assignmentType
    );

    @Update("""
            update user_area_assignment
            set status = 'ENDED', ended_at = current_timestamp(3)
            where area_id = #{areaId}
              and assignment_type = #{assignmentType}
              and status = 'ACTIVE'
            """)
    int endActiveAssignments(
            @Param("areaId") long areaId,
            @Param("assignmentType") String assignmentType
    );

    @Insert("""
            insert into user_area_assignment
              (area_id, user_id, assignment_type, is_primary, status, assigned_by)
            values
              (#{areaId}, #{userId}, #{assignmentType}, #{primaryFlag}, 'ACTIVE', #{assignedBy})
            """)
    int insertAssignment(
            @Param("areaId") long areaId,
            @Param("userId") long userId,
            @Param("assignmentType") String assignmentType,
            @Param("primaryFlag") boolean primaryFlag,
            @Param("assignedBy") long assignedBy
    );

    @Select("""
            select count(*)
            from sys_user u
            join sys_user_role ur on ur.user_id = u.id
            join sys_role r on r.id = ur.role_id
            where u.id = #{userId}
              and u.status = 'ENABLED'
              and ur.status = 'ACTIVE'
              and r.role_code = #{roleCode}
              and r.status = 'ENABLED'
            """)
    int countEnabledAssignee(
            @Param("userId") long userId,
            @Param("roleCode") String roleCode
    );

    @Select("""
            select id, area_code as code, area_name as name
            from grid_area
            where area_type = 'COMMUNITY' and status = 'ENABLED'
            order by area_code
            """)
    List<AreaOptionRow> findAllCommunities();

    @Select("""
            select distinct community.id, community.area_code as code, community.area_name as name
            from grid_area community
            where community.area_type = 'COMMUNITY'
              and community.status = 'ENABLED'
              and (
                exists (
                  select 1 from user_area_assignment assignment
                  where assignment.user_id = #{userId}
                    and assignment.area_id = community.id
                    and assignment.assignment_type = 'COMMUNITY_STAFF'
                    and assignment.status = 'ACTIVE'
                )
                or exists (
                  select 1
                  from user_area_assignment assignment
                  join grid_area grid on grid.id = assignment.area_id
                  where assignment.user_id = #{userId}
                    and assignment.assignment_type = 'GRID_WORKER'
                    and assignment.status = 'ACTIVE'
                    and grid.parent_id = community.id
                )
              )
            order by community.area_code
            """)
    List<AreaOptionRow> findAccessibleCommunities(@Param("userId") long userId);

    @Select("""
            select count(*)
            from user_area_assignment
            where user_id = #{userId}
              and area_id = #{communityId}
              and assignment_type = 'COMMUNITY_STAFF'
              and status = 'ACTIVE'
            """)
    int countCommunityStaffAccess(
            @Param("userId") long userId,
            @Param("communityId") long communityId
    );

    @Select("""
            select distinct u.id, u.username, u.real_name as realName
            from sys_user u
            join sys_user_role ur on ur.user_id = u.id
            join sys_role r on r.id = ur.role_id
            where u.status = 'ENABLED'
              and ur.status = 'ACTIVE'
              and r.role_code = #{roleCode}
              and r.status = 'ENABLED'
            order by u.real_name, u.id
            """)
    List<WorkerOptionRow> findAssigneeOptions(@Param("roleCode") String roleCode);

    record GridRow(
            Long id,
            Long communityId,
            String communityName,
            String areaCode,
            String areaName,
            String areaType,
            String address,
            String status,
            int version
    ) {
    }

    record GridDetailRow(
            Long id,
            Long communityId,
            String areaCode,
            String areaName,
            String areaType,
            String address,
            BigDecimal centerLongitude,
            BigDecimal centerLatitude,
            String boundaryGeojson,
            String status,
            int version
    ) {
    }

    record AssignmentRow(Long userId, String username, String realName, boolean primaryFlag) {
    }

    record AreaOptionRow(Long id, String code, String name) {
    }

    record WorkerOptionRow(Long id, String username, String realName) {
    }
}
