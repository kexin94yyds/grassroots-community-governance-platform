package com.cunzhi.governance.task.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskMapper {

    @Select("""
            <script>
            select count(*)
            from work_task t
            where 1 = 1
              <if test="keyword != null and keyword != ''">
                and (t.task_no like concat('%', #{keyword}, '%')
                     or t.title like concat('%', #{keyword}, '%'))
              </if>
              <if test="status != null and status != ''">
                and t.status = #{status}
              </if>
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and t.grid_id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                      #{gridId}
                    </foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            </script>
            """)
    long count(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            <script>
            select t.id, t.task_no as taskNo, t.source_event_id as sourceEventId,
                   source_event.event_no as sourceEventNo,
                   t.grid_id as gridId, grid.area_name as gridName,
                   t.task_type as taskType, t.title, t.description,
                   t.priority, t.status,
                   t.dispatcher_user_id as dispatcherUserId,
                   coalesce(dispatcher.real_name, dispatcher.username) as dispatcherName,
                   t.assignee_user_id as assigneeUserId,
                   coalesce(assignee.real_name, assignee.username) as assigneeName,
                   t.due_at as dueAt,
                   t.assigned_at as assignedAt,
                   t.handling_result as handlingResult, t.review_remark as reviewRemark,
                   t.version
            from work_task t
            left join governance_event source_event on source_event.id = t.source_event_id
            join grid_area grid on grid.id = t.grid_id
            join sys_user dispatcher on dispatcher.id = t.dispatcher_user_id
            join sys_user assignee on assignee.id = t.assignee_user_id
            where 1 = 1
              <if test="keyword != null and keyword != ''">
                and (t.task_no like concat('%', #{keyword}, '%')
                     or t.title like concat('%', #{keyword}, '%'))
              </if>
              <if test="status != null and status != ''">
                and t.status = #{status}
              </if>
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and t.grid_id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                      #{gridId}
                    </foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            order by coalesce(t.due_at, '9999-12-31 23:59:59'), t.id desc
            limit #{size} offset #{offset}
            </script>
            """)
    List<TaskRow> findPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            select t.id, t.task_no as taskNo, t.source_event_id as sourceEventId,
                   source_event.event_no as sourceEventNo,
                   t.grid_id as gridId, grid.area_name as gridName,
                   t.task_type as taskType, t.title, t.description, t.priority, t.status,
                   t.dispatcher_user_id as dispatcherUserId,
                   coalesce(dispatcher.real_name, dispatcher.username) as dispatcherName,
                   t.assignee_user_id as assigneeUserId,
                   coalesce(assignee.real_name, assignee.username) as assigneeName,
                   t.due_at as dueAt, t.assigned_at as assignedAt,
                   t.handling_result as handlingResult,
                   t.review_remark as reviewRemark, t.version
            from work_task t
            left join governance_event source_event on source_event.id = t.source_event_id
            join grid_area grid on grid.id = t.grid_id
            join sys_user dispatcher on dispatcher.id = t.dispatcher_user_id
            join sys_user assignee on assignee.id = t.assignee_user_id
            where t.id = #{id}
            """)
    Optional<TaskRow> findById(@Param("id") long id);

    @Select("""
            select t.id, t.task_no as taskNo, t.source_event_id as sourceEventId,
                   source_event.event_no as sourceEventNo,
                   t.grid_id as gridId, grid.area_name as gridName,
                   t.task_type as taskType, t.title, t.description, t.priority, t.status,
                   t.dispatcher_user_id as dispatcherUserId,
                   coalesce(dispatcher.real_name, dispatcher.username) as dispatcherName,
                   t.assignee_user_id as assigneeUserId,
                   coalesce(assignee.real_name, assignee.username) as assigneeName,
                   t.due_at as dueAt, t.assigned_at as assignedAt,
                   t.handling_result as handlingResult,
                   t.review_remark as reviewRemark, t.version
            from work_task t
            left join governance_event source_event on source_event.id = t.source_event_id
            join grid_area grid on grid.id = t.grid_id
            join sys_user dispatcher on dispatcher.id = t.dispatcher_user_id
            join sys_user assignee on assignee.id = t.assignee_user_id
            where t.id = #{id}
            for update
            """)
    Optional<TaskRow> findByIdForUpdate(@Param("id") long id);

    @Select("""
            select count(*) from work_task
            where source_event_id = #{eventId}
              and status not in ('COMPLETED', 'CANCELLED')
            """)
    int countActiveByEventId(@Param("eventId") long eventId);

    @Select("""
            select count(*) from work_task
            where grid_id = #{gridId}
              and assignee_user_id = #{assigneeUserId}
              and status not in ('COMPLETED', 'CANCELLED')
            """)
    int countUnfinishedByGridAndAssignee(
            @Param("gridId") long gridId,
            @Param("assigneeUserId") long assigneeUserId
    );

    @Insert("""
            insert into work_task
              (task_no, source_event_id, grid_id, task_type, title, description, priority,
               status, dispatcher_user_id, assignee_user_id, due_at)
            values
              (#{taskNo}, #{eventId}, #{gridId}, 'EVENT_HANDLE', #{title}, #{description}, #{priority},
               'PENDING_ACCEPT', #{dispatcherUserId}, #{assigneeUserId}, #{dueAt})
            """)
    int insertEventTask(
            @Param("taskNo") String taskNo,
            @Param("eventId") long eventId,
            @Param("gridId") long gridId,
            @Param("title") String title,
            @Param("description") String description,
            @Param("priority") String priority,
            @Param("dispatcherUserId") long dispatcherUserId,
            @Param("assigneeUserId") long assigneeUserId,
            @Param("dueAt") LocalDateTime dueAt
    );

    @Insert("""
            insert into work_task
              (task_no, source_event_id, grid_id, task_type, title, description, priority,
               status, dispatcher_user_id, assignee_user_id, due_at)
            values
              (#{taskNo}, null, #{gridId}, #{taskType}, #{title}, #{description}, #{priority},
               'PENDING_ACCEPT', #{dispatcherUserId}, #{assigneeUserId}, #{dueAt})
            """)
    int insertIndependentTask(
            @Param("taskNo") String taskNo,
            @Param("gridId") long gridId,
            @Param("taskType") String taskType,
            @Param("title") String title,
            @Param("description") String description,
            @Param("priority") String priority,
            @Param("dispatcherUserId") long dispatcherUserId,
            @Param("assigneeUserId") long assigneeUserId,
            @Param("dueAt") LocalDateTime dueAt
    );

    @Select("select id from work_task where task_no = #{taskNo}")
    long findIdByTaskNo(@Param("taskNo") String taskNo);

    @Update("""
            update work_task
            set status = #{toStatus},
                handling_result = coalesce(#{handlingResult}, handling_result),
                review_remark = coalesce(#{reviewRemark}, review_remark),
                accepted_at = case when #{fromStatus} = 'PENDING_ACCEPT' and #{toStatus} = 'PROCESSING'
                                   then current_timestamp(3) else accepted_at end,
                submitted_at = case when #{toStatus} = 'PENDING_REVIEW'
                                    then current_timestamp(3) else submitted_at end,
                completed_at = case when #{toStatus} = 'COMPLETED'
                                    then current_timestamp(3) else completed_at end,
                version = version + 1
            where id = #{id} and status = #{fromStatus} and version = #{version}
            """)
    int transition(
            @Param("id") long id,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("version") int version,
            @Param("handlingResult") String handlingResult,
            @Param("reviewRemark") String reviewRemark
    );

    record TaskRow(
            Long id,
            String taskNo,
            Long sourceEventId,
            String sourceEventNo,
            Long gridId,
            String gridName,
            String taskType,
            String title,
            String description,
            String priority,
            String status,
            Long dispatcherUserId,
            String dispatcherName,
            Long assigneeUserId,
            String assigneeName,
            LocalDateTime dueAt,
            LocalDateTime assignedAt,
            String handlingResult,
            String reviewRemark,
            int version
    ) {
    }
}
