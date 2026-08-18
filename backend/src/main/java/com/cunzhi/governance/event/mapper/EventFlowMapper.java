package com.cunzhi.governance.event.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface EventFlowMapper {

    @Insert("""
            insert into event_flow
              (event_id, task_id, action, from_status, to_status, operator_user_id, remark)
            values
              (#{eventId}, #{taskId}, #{action}, #{fromStatus}, #{toStatus}, #{operatorUserId}, #{remark})
            """)
    int insert(
            @Param("eventId") long eventId,
            @Param("taskId") Long taskId,
            @Param("action") String action,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("operatorUserId") long operatorUserId,
            @Param("remark") String remark
    );

    @Select("""
            select f.id, f.event_id as eventId, f.task_id as taskId, f.action,
                   f.from_status as fromStatus, f.to_status as toStatus,
                   f.operator_user_id as operatorUserId,
                   u.real_name as operatorName,
                   f.remark, f.created_at as createdAt
            from event_flow f
            left join sys_user u on u.id = f.operator_user_id
            where f.event_id = #{eventId}
            order by f.created_at, f.id
            """)
    List<EventFlowRow> findByEventId(@Param("eventId") long eventId);

    record EventFlowRow(
            Long id,
            Long eventId,
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
