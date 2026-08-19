package com.cunzhi.governance.serviceapplication.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface ServiceApplicationFlowMapper {

    @Insert("""
            insert into service_application_flow
              (application_id, action, from_status, to_status, operator_user_id, remark)
            values
              (#{applicationId}, #{action}, #{fromStatus}, #{toStatus}, #{operatorUserId}, #{remark})
            """)
    int insert(
            @Param("applicationId") long applicationId,
            @Param("action") String action,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("operatorUserId") long operatorUserId,
            @Param("remark") String remark
    );

    @Select("""
            select flow.id, flow.action, flow.from_status as fromStatus, flow.to_status as toStatus,
                   flow.operator_user_id as operatorUserId, operator_user.real_name as operatorName,
                   flow.remark, flow.created_at as createdAt
            from service_application_flow flow
            join sys_user operator_user on operator_user.id = flow.operator_user_id
            where flow.application_id = #{applicationId}
            order by flow.created_at, flow.id
            """)
    List<FlowRow> findByApplicationId(@Param("applicationId") long applicationId);

    record FlowRow(Long id, String action, String fromStatus, String toStatus,
                   Long operatorUserId, String operatorName, String remark, LocalDateTime createdAt) {
    }
}
