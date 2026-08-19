package com.cunzhi.governance.patrol.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface PatrolPlanMapper {

    @Select("""
            <script>
            select count(*) from patrol_plan plan
            where 1 = 1
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and plan.grid_id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            </script>
            """)
    long countScoped(@Param("allAccess") boolean allAccess, @Param("gridIds") List<Long> gridIds);

    @Select("""
            <script>
            select plan.id, plan.plan_no as planNo, plan.grid_id as gridId, grid.area_name as gridName,
                   plan.title, plan.inspection_content as inspectionContent,
                   plan.scheduled_at as scheduledAt, plan.due_at as dueAt,
                   plan.assignee_user_id as assigneeUserId, assignee.real_name as assigneeName,
                   plan.status, task.id as taskId, task.task_no as taskNo, task.status as taskStatus,
                   task.version as taskVersion, plan.created_by as createdBy, creator.real_name as createdByName,
                   plan.created_at as createdAt, plan.version
            from patrol_plan plan
            join grid_area grid on grid.id = plan.grid_id
            join sys_user assignee on assignee.id = plan.assignee_user_id
            join sys_user creator on creator.id = plan.created_by
            left join work_task task on task.patrol_plan_id = plan.id
            where 1 = 1
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and plan.grid_id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            order by plan.scheduled_at desc, plan.id desc
            limit #{size} offset #{offset}
            </script>
            """)
    List<PlanRow> findPageScoped(
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            select plan.id, plan.plan_no as planNo, plan.grid_id as gridId, grid.area_name as gridName,
                   plan.title, plan.inspection_content as inspectionContent,
                   plan.scheduled_at as scheduledAt, plan.due_at as dueAt,
                   plan.assignee_user_id as assigneeUserId, assignee.real_name as assigneeName,
                   plan.status, task.id as taskId, task.task_no as taskNo, task.status as taskStatus,
                   task.version as taskVersion, plan.created_by as createdBy, creator.real_name as createdByName,
                   plan.created_at as createdAt, plan.version
            from patrol_plan plan
            join grid_area grid on grid.id = plan.grid_id
            join sys_user assignee on assignee.id = plan.assignee_user_id
            join sys_user creator on creator.id = plan.created_by
            left join work_task task on task.patrol_plan_id = plan.id
            where plan.assignee_user_id = #{userId}
            order by plan.scheduled_at desc, plan.id desc
            limit 100
            """)
    List<PlanRow> findByAssigneeUserId(@Param("userId") long userId);

    @Select("""
            select plan.id, plan.plan_no as planNo, plan.grid_id as gridId, grid.area_name as gridName,
                   plan.title, plan.inspection_content as inspectionContent,
                   plan.scheduled_at as scheduledAt, plan.due_at as dueAt,
                   plan.assignee_user_id as assigneeUserId, assignee.real_name as assigneeName,
                   plan.status, task.id as taskId, task.task_no as taskNo, task.status as taskStatus,
                   task.version as taskVersion, plan.created_by as createdBy, creator.real_name as createdByName,
                   plan.created_at as createdAt, plan.version
            from patrol_plan plan
            join grid_area grid on grid.id = plan.grid_id
            join sys_user assignee on assignee.id = plan.assignee_user_id
            join sys_user creator on creator.id = plan.created_by
            left join work_task task on task.patrol_plan_id = plan.id
            where plan.id = #{id}
            """)
    PlanRow findById(@Param("id") long id);

    @Select("""
            select id, grid_id as gridId, assignee_user_id as assigneeUserId, status, version
            from patrol_plan where id = #{id} for update
            """)
    PlanLockRow findByIdForUpdate(@Param("id") long id);

    @Insert("""
            insert into patrol_plan
              (plan_no, grid_id, title, inspection_content, scheduled_at, due_at, assignee_user_id, status, created_by)
            values
              (#{planNo}, #{gridId}, #{title}, #{inspectionContent}, #{scheduledAt}, #{dueAt}, #{assigneeUserId}, 'ACTIVE', #{createdBy})
            """)
    int insert(
            @Param("planNo") String planNo,
            @Param("gridId") long gridId,
            @Param("title") String title,
            @Param("inspectionContent") String inspectionContent,
            @Param("scheduledAt") LocalDateTime scheduledAt,
            @Param("dueAt") LocalDateTime dueAt,
            @Param("assigneeUserId") long assigneeUserId,
            @Param("createdBy") long createdBy
    );

    @Select("select id from patrol_plan where plan_no = #{planNo}")
    Long findIdByPlanNo(@Param("planNo") String planNo);

    @Update("""
            update patrol_plan
            set status = 'CANCELLED', cancelled_at = current_timestamp(3), version = version + 1
            where id = #{id} and status = 'ACTIVE' and version = #{version}
            """)
    int cancel(@Param("id") long id, @Param("version") int version);

    @Select("select count(*) from patrol_plan where grid_id = #{gridId} and status = 'ACTIVE'")
    int countActiveByGridId(@Param("gridId") long gridId);

    record PlanRow(
            Long id, String planNo, Long gridId, String gridName, String title, String inspectionContent,
            LocalDateTime scheduledAt, LocalDateTime dueAt, Long assigneeUserId, String assigneeName,
            String status, Long taskId, String taskNo, String taskStatus, Integer taskVersion,
            Long createdBy, String createdByName, LocalDateTime createdAt, int version
    ) {
    }

    record PlanLockRow(Long id, Long gridId, Long assigneeUserId, String status, int version) {
    }
}
