package com.cunzhi.governance.system.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DataScopeMapper {

    @Select("""
            select count(*)
            from grid_area
            where id = #{gridId}
              and area_type = 'GRID'
              and status = 'ENABLED'
            """)
    int countEnabledGrid(@Param("gridId") long gridId);

    @Select("""
            select id from grid_area
            where id = #{gridId}
              and area_type = 'GRID'
              and status = 'ENABLED'
            for update
            """)
    Long lockEnabledGrid(@Param("gridId") long gridId);

    @Select("""
            select distinct scoped.grid_id
            from (
                select child.id as grid_id
                from user_area_assignment assignment
                join grid_area community on community.id = assignment.area_id
                join grid_area child on child.parent_id = community.id
                where assignment.user_id = #{userId}
                  and assignment.assignment_type = 'COMMUNITY_STAFF'
                  and assignment.status = 'ACTIVE'
                  and community.area_type = 'COMMUNITY'
                  and community.status = 'ENABLED'
                  and child.area_type = 'GRID'
                union all
                select grid.id as grid_id
                from user_area_assignment assignment
                join grid_area grid on grid.id = assignment.area_id
                where assignment.user_id = #{userId}
                  and assignment.assignment_type = 'GRID_WORKER'
                  and assignment.status = 'ACTIVE'
                  and grid.area_type = 'GRID'
                union all
                select resident.grid_id
                from resident
                where resident.user_id = #{userId}
                  and resident.status = 'ACTIVE'
            ) scoped
            """)
    List<Long> findAccessibleGridIds(@Param("userId") long userId);

    @Select("""
            select child.id
            from user_area_assignment assignment
            join grid_area community on community.id = assignment.area_id
            join grid_area child on child.parent_id = community.id
            where assignment.user_id = #{userId}
              and assignment.assignment_type = 'COMMUNITY_STAFF'
              and assignment.status = 'ACTIVE'
              and community.area_type = 'COMMUNITY'
              and community.status = 'ENABLED'
              and child.area_type = 'GRID'
              and child.status = 'ENABLED'
            """)
    List<Long> findCommunityGridIds(@Param("userId") long userId);

    @Select("""
            select grid.id
            from user_area_assignment assignment
            join grid_area grid on grid.id = assignment.area_id
            where assignment.user_id = #{userId}
              and assignment.assignment_type = 'GRID_WORKER'
              and assignment.status = 'ACTIVE'
              and grid.area_type = 'GRID'
              and grid.status = 'ENABLED'
            """)
    List<Long> findGridWorkerGridIds(@Param("userId") long userId);

    @Select("""
            select count(*)
            from user_area_assignment assignment
            join grid_area community on community.id = assignment.area_id
            where assignment.user_id = #{userId}
              and assignment.area_id = #{communityId}
              and assignment.assignment_type = 'COMMUNITY_STAFF'
              and assignment.status = 'ACTIVE'
              and community.area_type = 'COMMUNITY'
              and community.status = 'ENABLED'
            """)
    int countActiveCommunityStaffAccess(
            @Param("userId") long userId,
            @Param("communityId") long communityId
    );

    @Select("""
            <script>
            select count(*)
            from grid_area grid
            where grid.parent_id = #{communityId}
              and grid.area_type = 'GRID'
              and grid.id in
              <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
            </script>
            """)
    int countCommunityInGridScope(
            @Param("communityId") long communityId,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            select parent_id from grid_area
            where id = #{gridId} and area_type = 'GRID'
            """)
    Long findParentCommunityId(@Param("gridId") long gridId);

    @Select("""
            select id from grid_area
            where id = #{communityId} and area_type = 'COMMUNITY' and status = 'ENABLED'
            for update
            """)
    Long lockEnabledCommunityForUpdate(@Param("communityId") long communityId);

    @Select("""
            select id from user_area_assignment
            where user_id = #{userId}
              and area_id = #{communityId}
              and assignment_type = 'COMMUNITY_STAFF'
              and status = 'ACTIVE'
            for update
            """)
    Long lockActiveCommunityStaffAssignmentForUpdate(
            @Param("userId") long userId,
            @Param("communityId") long communityId
    );

    @Select("""
            select assignment.area_id
            from user_area_assignment assignment
            join grid_area community on community.id = assignment.area_id
            where assignment.user_id = #{userId}
              and assignment.assignment_type = 'COMMUNITY_STAFF'
              and assignment.status = 'ACTIVE'
              and community.area_type = 'COMMUNITY'
              and community.status = 'ENABLED'
            order by assignment.area_id
            """)
    List<Long> findActiveCommunityIds(@Param("userId") long userId);

    @Select("""
            select count(*) from grid_area
            where id = #{communityId} and area_type = 'COMMUNITY' and status = 'ENABLED'
            """)
    int countEnabledCommunity(@Param("communityId") long communityId);

    @Select("""
            select count(*)
            from user_area_assignment assignment
            join grid_area grid on grid.id = assignment.area_id
            join sys_user u on u.id = assignment.user_id
            join sys_user_role ur on ur.user_id = u.id and ur.status = 'ACTIVE'
            join sys_role r on r.id = ur.role_id
            where assignment.user_id = #{userId}
              and assignment.area_id = #{gridId}
              and assignment.assignment_type = 'GRID_WORKER'
              and assignment.status = 'ACTIVE'
              and grid.area_type = 'GRID'
              and grid.status = 'ENABLED'
              and u.status = 'ENABLED'
              and r.role_code = 'GRID_WORKER'
              and r.status = 'ENABLED'
            """)
    int countActiveGridWorkerAssignment(
            @Param("userId") long userId,
            @Param("gridId") long gridId
    );
}
