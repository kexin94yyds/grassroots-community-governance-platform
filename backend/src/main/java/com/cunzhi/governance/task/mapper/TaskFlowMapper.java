package com.cunzhi.governance.task.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskFlowMapper {

    @Insert("""
            insert into task_flow
              (task_id, action, from_status, to_status, operator_user_id, remark)
            values
              (#{taskId}, #{action}, #{fromStatus}, #{toStatus}, #{operatorUserId}, #{remark})
            """)
    int insert(
            @Param("taskId") long taskId,
            @Param("action") String action,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("operatorUserId") long operatorUserId,
            @Param("remark") String remark
    );

    @Select("""
            select f.id, f.task_id as taskId, f.action,
                   f.from_status as fromStatus, f.to_status as toStatus,
                   f.operator_user_id as operatorUserId,
                   u.real_name as operatorName,
                   f.remark, f.created_at as createdAt
            from task_flow f
            left join sys_user u on u.id = f.operator_user_id
            where f.task_id = #{taskId}
            order by f.created_at, f.id
            """)
    List<TaskFlowRow> findByTaskId(@Param("taskId") long taskId);

    record TaskFlowRow(
            Long id,
            Long taskId,
            String action,
            String fromStatus,
            String toStatus,
            Long operatorUserId,
            String operatorName,
            String remark,
            LocalDateTime createdAt
    ) {
    }
}
