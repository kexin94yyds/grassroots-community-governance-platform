package com.cunzhi.governance.workbench.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkbenchMapper {

    @Select("""
            select
              (select count(*) from sys_user) as totalUsers,
              (select count(*) from sys_user where approval_status = 'PENDING') as pendingRegistrations,
              (select count(*) from community_announcement where status = 'PUBLISHED') as publishedAnnouncements,
              (select count(*) from service_application where status not in ('COMPLETED', 'REJECTED', 'CANCELLED')) as openApplications,
              (select count(*) from patrol_plan where status = 'ACTIVE') as activePatrolPlans,
              (select count(*) from governance_event where status not in ('CLOSED', 'REJECTED', 'CANCELLED')) as openEvents
            """)
    AdminMetricRow adminMetrics();

    @Select("""
            <script>
            with scoped_grid as (
              select id from grid_area where area_type = 'GRID' and id in
              <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
            )
            select
              (select count(*) from governance_event e join scoped_grid g on g.id = e.grid_id where e.status = 'REPORTED') as reportedEvents,
              (select count(*) from work_task t join scoped_grid g on g.id = t.grid_id where t.status = 'PENDING_REVIEW') as pendingReviews,
              (select count(*) from service_application a join scoped_grid g on g.id = a.grid_id where a.status in ('SUBMITTED', 'ACCEPTED', 'PROCESSING')) as openApplications,
              (select count(*) from patrol_plan p join scoped_grid g on g.id = p.grid_id where p.status = 'ACTIVE') as activePatrolPlans,
              (select count(*) from resident r join scoped_grid g on g.id = r.grid_id where r.status = 'ACTIVE') as activeResidents
            </script>
            """)
    CommunityMetricRow communityMetrics(@Param("gridIds") List<Long> gridIds);

    @Select("""
            <script>
            select
              count(case when t.status = 'PENDING_ACCEPT' then 1 end) as pendingAccept,
              count(case when t.status = 'PROCESSING' then 1 end) as processing,
              count(case when t.status = 'PENDING_REVIEW' then 1 end) as pendingReview,
              count(case when t.due_at is not null and t.due_at &lt; current_timestamp(3)
                          and t.status not in ('COMPLETED', 'CANCELLED') then 1 end) as overdue,
              (select count(*) from patrol_plan p where p.assignee_user_id = #{userId} and p.status = 'ACTIVE') as activePatrolPlans,
              (select count(*) from governance_event e where e.reporter_user_id = #{userId}
                 and e.reported_at &gt;= date_sub(current_timestamp(3), interval 7 day)) as reportsLast7Days
            from work_task t
            where t.assignee_user_id = #{userId}
              and t.grid_id in
              <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
            </script>
            """)
    GridMetricRow gridMetrics(@Param("userId") long userId, @Param("gridIds") List<Long> gridIds);

    @Select("""
            select
              (select count(*) from governance_event where reporter_user_id = #{userId}
                 and status not in ('CLOSED', 'REJECTED', 'CANCELLED')) as openEvents,
              (select count(*) from service_application where applicant_user_id = #{userId}
                 and status in ('SUBMITTED', 'ACCEPTED', 'PROCESSING')) as openApplications,
              (select count(*) from service_application where applicant_user_id = #{userId}
                 and status = 'COMPLETED' and rating is null) as pendingRatings,
              (select count(*) from community_announcement announcement
                where announcement.status = 'PUBLISHED'
                  and (announcement.audience_scope = 'GLOBAL' or announcement.community_id = #{communityId})) as visibleAnnouncements
            """)
    ResidentMetricRow residentMetrics(@Param("userId") long userId, @Param("communityId") Long communityId);

    @Select("""
            select cast(u.id as char) as id, concat('注册申请：', u.real_name) as title,
                   u.approval_status as status, '/system/users' as route, u.created_at as occurredAt
            from sys_user u where u.approval_status = 'PENDING'
            order by u.created_at desc, u.id desc limit 10
            """)
    List<ItemRow> findAdminFocus();

    @Select("""
            <script>
            select cast(application.id as char) as id, concat('服务申请：', catalog.service_name) as title,
                   application.status, '/community/service' as route, application.created_at as occurredAt
            from service_application application
            join service_catalog catalog on catalog.id = application.service_catalog_id
            where application.status in ('SUBMITTED', 'ACCEPTED', 'PROCESSING')
              and application.grid_id in
              <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
            order by application.created_at desc, application.id desc limit 10
            </script>
            """)
    List<ItemRow> findCommunityFocus(@Param("gridIds") List<Long> gridIds);

    @Select("""
            select cast(task.id as char) as id, task.title, task.status, '/grid/tasks' as route,
                   coalesce(task.due_at, task.assigned_at) as occurredAt
            from work_task task
            where task.assignee_user_id = #{userId}
              and task.status not in ('COMPLETED', 'CANCELLED')
            order by coalesce(task.due_at, task.assigned_at), task.id desc limit 10
            """)
    List<ItemRow> findGridFocus(@Param("userId") long userId);

    @Select("""
            select cast(application.id as char) as id, concat('服务申请：', catalog.service_name) as title,
                   application.status, '/resident/service' as route, application.created_at as occurredAt
            from service_application application
            join service_catalog catalog on catalog.id = application.service_catalog_id
            where application.applicant_user_id = #{userId}
            order by application.created_at desc, application.id desc limit 10
            """)
    List<ItemRow> findResidentFocus(@Param("userId") long userId);

    @Select("""
            select cast(announcement.id as char) as id, announcement.title, announcement.status,
                   '/announcements' as route, coalesce(announcement.published_at, announcement.created_at) as occurredAt
            from community_announcement announcement
            order by announcement.created_at desc, announcement.id desc limit 10
            """)
    List<ItemRow> findAdminRecent();

    @Select("""
            <script>
            select cast(event.id as char) as id, event.title, event.status, '/events' as route, event.reported_at as occurredAt
            from governance_event event
            where event.grid_id in
            <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
            order by event.reported_at desc, event.id desc limit 10
            </script>
            """)
    List<ItemRow> findCommunityRecent(@Param("gridIds") List<Long> gridIds);

    @Select("""
            select cast(task.id as char) as id, task.title, task.status, '/grid/history' as route,
                   coalesce(task.completed_at, task.assigned_at) as occurredAt
            from work_task task where task.assignee_user_id = #{userId}
            order by coalesce(task.completed_at, task.assigned_at) desc, task.id desc limit 10
            """)
    List<ItemRow> findGridRecent(@Param("userId") long userId);

    @Select("""
            select cast(announcement.id as char) as id, announcement.title, announcement.status,
                   '/announcements' as route, announcement.published_at as occurredAt
            from community_announcement announcement
            where announcement.status = 'PUBLISHED'
              and (announcement.audience_scope = 'GLOBAL' or announcement.community_id = #{communityId})
            order by announcement.pinned desc, announcement.published_at desc, announcement.id desc limit 10
            """)
    List<ItemRow> findResidentRecent(@Param("communityId") Long communityId);

    @Select("""
            <script>
            select * from (
              select concat('EVENT-', flow.id) as id, flow.created_at as createdAt,
                     'EVENT' as module, '治理事件' as moduleLabel, flow.action,
                     flow.action as actionLabel, operator_user.real_name as operatorName,
                     concat('事件：', event.event_no) as objectLabel, grid.area_name as scopeLabel,
                     'SUCCESS' as result, '已记录' as resultLabel
              from event_flow flow
              join governance_event event on event.id = flow.event_id
              join grid_area grid on grid.id = event.grid_id
              left join sys_user operator_user on operator_user.id = flow.operator_user_id
              union all
              select concat('TASK-', flow.id), flow.created_at,
                     'TASK', '网格任务', flow.action, flow.action,
                     operator_user.real_name, concat('任务：', task.task_no), grid.area_name,
                     'SUCCESS', '已记录'
              from task_flow flow
              join work_task task on task.id = flow.task_id
              join grid_area grid on grid.id = task.grid_id
              left join sys_user operator_user on operator_user.id = flow.operator_user_id
              union all
              select concat('SENSITIVE-', log.id), log.created_at,
                     'RESIDENT_SENSITIVE', '敏感访问', log.action, log.action,
                     operator_user.real_name, '居民敏感访问记录', coalesce(grid.area_name, '历史范围未知'),
                     'SUCCESS', '已记录'
              from resident_sensitive_access_log log
              join sys_user operator_user on operator_user.id = log.operator_user_id
              left join grid_area grid on grid.id = log.scope_grid_id
              union all
              select concat('ANNOUNCEMENT-', flow.id), flow.created_at,
                     'ANNOUNCEMENT', '社区公告', flow.action, flow.action,
                     operator_user.real_name, concat('公告：', announcement.announcement_no),
                     case when announcement.audience_scope = 'GLOBAL' then '全局' else coalesce(community.area_name, '社区') end,
                     'SUCCESS', '已记录'
              from announcement_flow flow
              join community_announcement announcement on announcement.id = flow.announcement_id
              left join grid_area community on community.id = announcement.community_id
              join sys_user operator_user on operator_user.id = flow.operator_user_id
              union all
              select concat('SERVICE-', flow.id), flow.created_at,
                     'SERVICE_APPLICATION', '服务申请', flow.action, flow.action,
                     operator_user.real_name, concat('服务申请：', application.application_no), grid.area_name,
                     'SUCCESS', '已记录'
              from service_application_flow flow
              join service_application application on application.id = flow.application_id
              join grid_area grid on grid.id = application.grid_id
              join sys_user operator_user on operator_user.id = flow.operator_user_id
            ) operation_rows
            where 1 = 1
              <if test="module != null and module != ''">and module = #{module}</if>
              <if test="keyword != null and keyword != ''">
                and (operatorName like concat('%', #{keyword}, '%')
                     or objectLabel like concat('%', #{keyword}, '%')
                     or scopeLabel like concat('%', #{keyword}, '%'))
              </if>
            order by createdAt desc, id desc
            limit #{size} offset #{offset}
            </script>
            """)
    List<OperationItemRow> findOperations(
            @Param("module") String module,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            <script>
            select count(*) from (
              select flow.created_at as createdAt, 'EVENT' as module, operator_user.real_name as operatorName,
                     concat('事件：', event.event_no) as objectLabel, grid.area_name as scopeLabel
              from event_flow flow join governance_event event on event.id = flow.event_id
              join grid_area grid on grid.id = event.grid_id left join sys_user operator_user on operator_user.id = flow.operator_user_id
              union all
              select flow.created_at, 'TASK', operator_user.real_name, concat('任务：', task.task_no), grid.area_name
              from task_flow flow join work_task task on task.id = flow.task_id
              join grid_area grid on grid.id = task.grid_id left join sys_user operator_user on operator_user.id = flow.operator_user_id
              union all
              select log.created_at, 'RESIDENT_SENSITIVE', operator_user.real_name, '居民敏感访问记录', coalesce(grid.area_name, '历史范围未知')
              from resident_sensitive_access_log log join sys_user operator_user on operator_user.id = log.operator_user_id
              left join grid_area grid on grid.id = log.scope_grid_id
              union all
              select flow.created_at, 'ANNOUNCEMENT', operator_user.real_name, concat('公告：', announcement.announcement_no),
                     case when announcement.audience_scope = 'GLOBAL' then '全局' else coalesce(community.area_name, '社区') end
              from announcement_flow flow join community_announcement announcement on announcement.id = flow.announcement_id
              left join grid_area community on community.id = announcement.community_id join sys_user operator_user on operator_user.id = flow.operator_user_id
              union all
              select flow.created_at, 'SERVICE_APPLICATION', operator_user.real_name, concat('服务申请：', application.application_no), grid.area_name
              from service_application_flow flow join service_application application on application.id = flow.application_id
              join grid_area grid on grid.id = application.grid_id join sys_user operator_user on operator_user.id = flow.operator_user_id
            ) operation_rows
            where 1 = 1
              <if test="module != null and module != ''">and module = #{module}</if>
              <if test="keyword != null and keyword != ''">
                and (operatorName like concat('%', #{keyword}, '%')
                     or objectLabel like concat('%', #{keyword}, '%')
                     or scopeLabel like concat('%', #{keyword}, '%'))
              </if>
            </script>
            """)
    long countOperations(@Param("module") String module, @Param("keyword") String keyword);

    @Select("""
            select
              (select count(*) from event_flow) as eventFlowCount,
              (select count(*) from task_flow) as taskFlowCount,
              (select count(*) from resident_sensitive_access_log) as sensitiveAccessCount,
              (select count(*) from announcement_flow) as announcementFlowCount,
              (select count(*) from service_application_flow) as serviceApplicationFlowCount
            """)
    OperationRow operationCounts();

    @Select("select current_timestamp(3)")
    LocalDateTime databaseTime();

    @Select("select version from flyway_schema_history where success = 1 order by installed_rank desc limit 1")
    String latestFlywayVersion();

    @Select("""
            select
              (select count(*) from sys_user) as userCount,
              (select count(*) from sys_user where status = 'ENABLED') as enabledUserCount,
              (select count(*) from sys_role) as roleCount,
              (select count(*) from sys_menu) as menuCount,
              (select count(*) from governance_event) as eventCount,
              (select count(*) from work_task) as taskCount,
              (select count(*) from community_announcement) as announcementCount,
              (select count(*) from service_application) as serviceApplicationCount,
              (select count(*) from patrol_plan) as patrolPlanCount
            """)
    HealthCountRow healthCounts();

    @Select("""
            select
              (select count(*) from patrol_plan plan left join work_task task on task.patrol_plan_id = plan.id where task.id is null) as patrolWithoutTask,
              (select count(*) from patrol_plan plan join work_task task on task.patrol_plan_id = plan.id
                 where plan.status = 'ACTIVE' and task.status in ('COMPLETED', 'CANCELLED')) as activePatrolWithTerminalTask,
              (select count(*) from patrol_plan plan join work_task task on task.patrol_plan_id = plan.id
                 where plan.status = 'CANCELLED' and task.status <> 'CANCELLED') as cancelledPatrolWithNonCancelledTask,
              (select count(*) from patrol_plan plan join work_task task on task.patrol_plan_id = plan.id
                 where plan.status = 'COMPLETED' and task.status <> 'COMPLETED') as completedPatrolWithNonCompletedTask,
              (select count(*) from service_application where status = 'COMPLETED' and completed_at is null) as incompleteServiceTimestamp
            """)
    ConsistencyRow consistencyCounts();

    record AdminMetricRow(long totalUsers, long pendingRegistrations, long publishedAnnouncements,
                          long openApplications, long activePatrolPlans, long openEvents) {
    }

    record CommunityMetricRow(long reportedEvents, long pendingReviews, long openApplications,
                              long activePatrolPlans, long activeResidents) {
    }

    record GridMetricRow(long pendingAccept, long processing, long pendingReview, long overdue,
                          long activePatrolPlans, long reportsLast7Days) {
    }

    record ResidentMetricRow(long openEvents, long openApplications, long pendingRatings, long visibleAnnouncements) {
    }

    record ItemRow(String id, String title, String status, String route, LocalDateTime occurredAt) {
    }

    record OperationRow(long eventFlowCount, long taskFlowCount, long sensitiveAccessCount,
                        long announcementFlowCount, long serviceApplicationFlowCount) {
    }

    record OperationItemRow(String id, LocalDateTime createdAt, String module, String moduleLabel,
                            String action, String actionLabel, String operatorName, String objectLabel,
                            String scopeLabel, String result, String resultLabel) {
    }

    record HealthCountRow(long userCount, long enabledUserCount, long roleCount, long menuCount, long eventCount, long taskCount,
                          long announcementCount, long serviceApplicationCount, long patrolPlanCount) {
    }

    record ConsistencyRow(
            long patrolWithoutTask,
            long activePatrolWithTerminalTask,
            long cancelledPatrolWithNonCancelledTask,
            long completedPatrolWithNonCompletedTask,
            long incompleteServiceTimestamp
    ) {
    }
}
