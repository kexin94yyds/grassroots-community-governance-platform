package com.cunzhi.governance.insight.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

public interface InsightMapper {

    @Select("""
            select
              count(distinct u.id) as totalCount,
              count(distinct case when u.status = 'ENABLED' then u.id end) as enabledCount,
              count(distinct case when u.status = 'DISABLED' then u.id end) as disabledCount,
              count(distinct case when u.status = 'LOCKED' then u.id end) as lockedCount,
              count(distinct case
                when u.last_login_at >= date_sub(current_timestamp(3), interval 30 day) then u.id
              end) as loggedInLast30DaysCount,
              count(distinct case when role.role_code = 'SYSTEM_ADMIN' then u.id end) as systemAdminCount,
              count(distinct case when role.role_code = 'COMMUNITY_STAFF' then u.id end) as communityStaffCount,
              count(distinct case when role.role_code = 'GRID_WORKER' then u.id end) as gridWorkerCount
            from sys_user u
            left join sys_user_role user_role
              on user_role.user_id = u.id and user_role.status = 'ACTIVE'
            left join sys_role role
              on role.id = user_role.role_id and role.status = 'ENABLED'
            """)
    UserInsightRow users();

    @Select("""
            <script>
            select
              community.id as communityId,
              community.area_code as communityCode,
              community.area_name as communityName,
              community.status as communityStatus,
              grid.id as gridId,
              grid.area_code as gridCode,
              grid.area_name as gridName,
              grid.status as gridStatus,
              grid.address,
              grid.center_longitude as centerLongitude,
              grid.center_latitude as centerLatitude,
              worker.id as workerId,
              worker.real_name as workerName,
              assignment.is_primary as primaryWorker
            from grid_area grid
            join grid_area community
              on community.id = grid.parent_id and community.area_type = 'COMMUNITY'
            left join user_area_assignment assignment
              on assignment.area_id = grid.id
             and assignment.assignment_type = 'GRID_WORKER'
             and assignment.status = 'ACTIVE'
            left join sys_user worker
              on worker.id = assignment.user_id
            where grid.area_type = 'GRID'
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and grid.id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                      #{gridId}
                    </foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            order by community.area_code, grid.area_code, assignment.is_primary desc, worker.real_name
            </script>
            """)
    List<GridTopologyRow> gridTopology(
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            <script>
            with scoped_grid as (
              select id
              from grid_area
              where area_type = 'GRID'
                <if test="!allAccess">
                  <choose>
                    <when test="gridIds != null and gridIds.size() > 0">
                      and id in
                      <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                        #{gridId}
                      </foreach>
                    </when>
                    <otherwise>and 1 = 0</otherwise>
                  </choose>
                </if>
            )
            select
              (select count(*) from resident r join scoped_grid g on g.id = r.grid_id) as residentCount,
              (select count(*) from household h join scoped_grid g on g.id = h.grid_id) as householdCount,
              (select count(*) from resident r join scoped_grid g on g.id = r.grid_id
               where r.status = 'ACTIVE') as activeCount,
              (select count(*) from resident r join scoped_grid g on g.id = r.grid_id
               where r.status = 'MOVED') as movedCount,
              (select count(*) from resident r join scoped_grid g on g.id = r.grid_id
               where r.status = 'DECEASED') as deceasedCount,
              (select count(*) from resident r join scoped_grid g on g.id = r.grid_id
               where r.status = 'ARCHIVED') as archivedCount,
              (select count(*) from resident r join scoped_grid g on g.id = r.grid_id
               where r.status = 'ACTIVE' and json_length(r.special_group_tags) > 0) as keyPopulationCount
            </script>
            """)
    ResidentInsightRow residents(
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            <script>
            with scoped_grid as (
              select id
              from grid_area
              where area_type = 'GRID'
                <if test="!allAccess">
                  <choose>
                    <when test="gridIds != null and gridIds.size() > 0">
                      and id in
                      <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                        #{gridId}
                      </foreach>
                    </when>
                    <otherwise>and 1 = 0</otherwise>
                  </choose>
                </if>
            )
            select tags.tag as breakdownKey, count(*) as itemCount
            from resident resident
            join scoped_grid grid on grid.id = resident.grid_id
            join json_table(
              coalesce(resident.special_group_tags, json_array()),
              '$[*]' columns(tag varchar(80) path '$')
            ) tags
            where resident.status = 'ACTIVE'
            group by tags.tag
            order by itemCount desc, tags.tag
            </script>
            """)
    List<BreakdownRow> residentSpecialGroups(
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            <script>
            with scoped_grid as (
              select id
              from grid_area
              where area_type = 'GRID'
                <if test="!allAccess">
                  <choose>
                    <when test="gridIds != null and gridIds.size() > 0">
                      and id in
                      <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                        #{gridId}
                      </foreach>
                    </when>
                    <otherwise>and 1 = 0</otherwise>
                  </choose>
                </if>
            )
            select
              count(*) as totalCount,
              count(case when event.status = 'REPORTED' then 1 end) as reportedCount,
              count(case when event.status = 'ACCEPTED' then 1 end) as acceptedCount,
              count(case when event.status = 'ASSIGNED' then 1 end) as assignedCount,
              count(case when event.status = 'PROCESSING' then 1 end) as processingCount,
              count(case when event.status = 'PENDING_REVIEW' then 1 end) as pendingReviewCount,
              count(case when event.status = 'CLOSED' then 1 end) as closedCount,
              count(case when event.status = 'REJECTED' then 1 end) as rejectedCount,
              count(case when event.status = 'CANCELLED' then 1 end) as cancelledCount,
              count(case when event.severity = 'LOW' then 1 end) as lowCount,
              count(case when event.severity = 'MEDIUM' then 1 end) as mediumCount,
              count(case when event.severity = 'HIGH' then 1 end) as highCount,
              count(case when event.severity = 'URGENT' then 1 end) as urgentCount
            from governance_event event
            join scoped_grid grid on grid.id = event.grid_id
            </script>
            """)
    EventInsightRow events(
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            <script>
            with scoped_grid as (
              select id
              from grid_area
              where area_type = 'GRID'
                <if test="!allAccess">
                  <choose>
                    <when test="gridIds != null and gridIds.size() > 0">
                      and id in
                      <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                        #{gridId}
                      </foreach>
                    </when>
                    <otherwise>and 1 = 0</otherwise>
                  </choose>
                </if>
            )
            select
              count(*) as totalCount,
              count(case when task.status = 'PENDING_ACCEPT' then 1 end) as pendingAcceptCount,
              count(case when task.status = 'PROCESSING' then 1 end) as processingCount,
              count(case when task.status = 'PENDING_REVIEW' then 1 end) as pendingReviewCount,
              count(case when task.status = 'COMPLETED' then 1 end) as completedCount,
              count(case when task.status = 'CANCELLED' then 1 end) as cancelledCount,
              count(case when task.priority = 'LOW' then 1 end) as lowCount,
              count(case when task.priority = 'MEDIUM' then 1 end) as mediumCount,
              count(case when task.priority = 'HIGH' then 1 end) as highCount,
              count(case when task.priority = 'URGENT' then 1 end) as urgentCount,
              count(case
                when task.due_at is not null
                 and task.due_at &lt; current_timestamp(3)
                 and task.status not in ('COMPLETED', 'CANCELLED')
                then 1
              end) as overdueCount
            from work_task task
            join scoped_grid grid on grid.id = task.grid_id
            </script>
            """)
    TaskInsightRow tasks(
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    record UserInsightRow(
            long totalCount,
            long enabledCount,
            long disabledCount,
            long lockedCount,
            long loggedInLast30DaysCount,
            long systemAdminCount,
            long communityStaffCount,
            long gridWorkerCount
    ) {
    }

    record GridTopologyRow(
            Long communityId,
            String communityCode,
            String communityName,
            String communityStatus,
            Long gridId,
            String gridCode,
            String gridName,
            String gridStatus,
            String address,
            BigDecimal centerLongitude,
            BigDecimal centerLatitude,
            Long workerId,
            String workerName,
            Boolean primaryWorker
    ) {
    }

    record ResidentInsightRow(
            long residentCount,
            long householdCount,
            long activeCount,
            long movedCount,
            long deceasedCount,
            long archivedCount,
            long keyPopulationCount
    ) {
    }

    record BreakdownRow(String breakdownKey, long itemCount) {
    }

    record EventInsightRow(
            long totalCount,
            long reportedCount,
            long acceptedCount,
            long assignedCount,
            long processingCount,
            long pendingReviewCount,
            long closedCount,
            long rejectedCount,
            long cancelledCount,
            long lowCount,
            long mediumCount,
            long highCount,
            long urgentCount
    ) {
    }

    record TaskInsightRow(
            long totalCount,
            long pendingAcceptCount,
            long processingCount,
            long pendingReviewCount,
            long completedCount,
            long cancelledCount,
            long lowCount,
            long mediumCount,
            long highCount,
            long urgentCount,
            long overdueCount
    ) {
    }
}
