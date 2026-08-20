package com.cunzhi.governance.workbench.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface OperationAuditMapper {

    @Insert("""
            insert into operation_audit_log
              (operator_user_id, module, action, request_method, object_path, result, status_code)
            values
              (#{operatorUserId}, #{module}, #{action}, #{requestMethod}, #{objectPath}, #{result}, #{statusCode})
            """)
    int insert(
            @Param("operatorUserId") Long operatorUserId,
            @Param("module") String module,
            @Param("action") String action,
            @Param("requestMethod") String requestMethod,
            @Param("objectPath") String objectPath,
            @Param("result") String result,
            @Param("statusCode") int statusCode
    );
}
