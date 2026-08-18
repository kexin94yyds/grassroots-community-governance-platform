package com.cunzhi.governance.dashboard.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DashboardMapper {

    @Select("""
            <script>
            with scoped_grid as (
              select id from grid_area
              where area_type = 'GRID'
                and status = 'ENABLED'
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
              (select count(*) from scoped_grid) as gridCount,
              (select count(*) from resident r join scoped_grid g on g.id = r.grid_id
               where r.status = 'ACTIVE') as residentCount,
              (select count(*) from resident r join scoped_grid g on g.id = r.grid_id
               where r.status = 'ACTIVE' and json_length(r.special_group_tags) > 0) as keyPopulationCount,
              (select count(*) from governance_event e join scoped_grid g on g.id = e.grid_id
               where e.status = 'REPORTED') as pendingEventCount,
              (select count(*) from governance_event e join scoped_grid g on g.id = e.grid_id
               where e.status in ('ACCEPTED', 'ASSIGNED', 'PROCESSING')) as processingEventCount,
              (select count(*) from governance_event e join scoped_grid g on g.id = e.grid_id
               where e.status = 'PENDING_REVIEW') as pendingReviewEventCount,
              (select count(*) from governance_event e join scoped_grid g on g.id = e.grid_id
               where e.status = 'CLOSED') as closedEventCount
            </script>
            """)
    DashboardRow overview(
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            <script>
            select g.id as gridId, g.area_code as gridCode, g.area_name as gridName,
                   count(distinct e.id) as eventCount,
                   count(distinct case when t.source_event_id is not null
                                            and t.status = 'COMPLETED'
                                            and t.due_at is not null then t.id end) as completedWithDeadlineCount,
                   count(distinct case when t.source_event_id is not null
                                            and t.status = 'COMPLETED'
                                            and t.due_at is not null
                                            and t.completed_at &lt;= t.due_at then t.id end) as onTimeClosedCount
            from grid_area g
            left join governance_event e on e.grid_id = g.id
            left join work_task t on t.source_event_id = e.id
            where g.area_type = 'GRID' and g.status = 'ENABLED'
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and g.id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                      #{gridId}
                    </foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            group by g.id, g.area_code, g.area_name
            order by g.area_code, g.id
            </script>
            """)
    List<GridEventStatRow> findGridEventStats(
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            <script>
            select c.id as categoryId, c.category_name as categoryName, count(e.id) as eventCount
            from event_category c
            left join governance_event e on e.category_id = c.id
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and e.grid_id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                      #{gridId}
                    </foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            group by c.id, c.category_name, c.sort_no
            order by eventCount desc, c.sort_no, c.id
            </script>
            """)
    List<CategoryStatRow> findCategoryStats(
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            <script>
            select e.id, e.event_no as eventNo, e.title,
                   c.category_name as categoryName, g.area_name as gridName,
                   e.status, e.severity, e.reported_at as reportedAt
            from governance_event e
            join event_category c on c.id = e.category_id
            join grid_area g on g.id = e.grid_id
            where 1 = 1
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and e.grid_id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                      #{gridId}
                    </foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            order by e.reported_at desc, e.id desc
            limit 10
            </script>
            """)
    List<RecentEventRow> findRecentEvents(
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    record DashboardRow(
            long gridCount,
            long residentCount,
            long keyPopulationCount,
            long pendingEventCount,
            long processingEventCount,
            long pendingReviewEventCount,
            long closedEventCount
    ) {
    }

    record GridEventStatRow(
            Long gridId, String gridCode, String gridName, long eventCount,
            long completedWithDeadlineCount, long onTimeClosedCount
    ) {
    }

    record CategoryStatRow(Long categoryId, String categoryName, long eventCount) {
    }

    record RecentEventRow(
            Long id, String eventNo, String title, String categoryName, String gridName,
            String status, String severity, java.time.LocalDateTime reportedAt
    ) {
    }
}
