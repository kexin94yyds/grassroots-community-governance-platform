package com.cunzhi.governance.serviceapplication.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ServiceApplicationMapper {

    @Select("""
            <script>
            select count(*) from service_application application
            where 1 = 1
              <if test="status != null and status != ''">and application.status = #{status}</if>
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and application.grid_id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            </script>
            """)
    long countScoped(
            @Param("status") String status,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            <script>
            select application.id, application.application_no as applicationNo,
                   application.resident_id as residentId, resident.real_name as residentName,
                   application.applicant_user_id as applicantUserId,
                   application.grid_id as gridId, grid.area_name as gridName,
                   application.service_catalog_id as serviceCatalogId, catalog.service_name as serviceCatalogName,
                   application.request_content as requestContent, application.appointment_at as appointmentAt,
                   application.status, application.handler_user_id as handlerUserId,
                   handler.real_name as handlerName, application.result_summary as resultSummary,
                   application.rating, application.rating_remark as ratingRemark,
                   application.created_at as createdAt, application.completed_at as completedAt, application.version
            from service_application application
            join resident resident on resident.id = application.resident_id
            join grid_area grid on grid.id = application.grid_id
            join service_catalog catalog on catalog.id = application.service_catalog_id
            left join sys_user handler on handler.id = application.handler_user_id
            where 1 = 1
              <if test="status != null and status != ''">and application.status = #{status}</if>
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and application.grid_id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            order by application.created_at desc, application.id desc
            limit #{size} offset #{offset}
            </script>
            """)
    List<ApplicationRow> findPageScoped(
            @Param("status") String status,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            select application.id, application.application_no as applicationNo,
                   application.resident_id as residentId, resident.real_name as residentName,
                   application.applicant_user_id as applicantUserId,
                   application.grid_id as gridId, grid.area_name as gridName,
                   application.service_catalog_id as serviceCatalogId, catalog.service_name as serviceCatalogName,
                   application.request_content as requestContent, application.appointment_at as appointmentAt,
                   application.status, application.handler_user_id as handlerUserId,
                   handler.real_name as handlerName, application.result_summary as resultSummary,
                   application.rating, application.rating_remark as ratingRemark,
                   application.created_at as createdAt, application.completed_at as completedAt, application.version
            from service_application application
            join resident resident on resident.id = application.resident_id
            join grid_area grid on grid.id = application.grid_id
            join service_catalog catalog on catalog.id = application.service_catalog_id
            left join sys_user handler on handler.id = application.handler_user_id
            where application.applicant_user_id = #{userId}
            order by application.created_at desc, application.id desc
            limit 100
            """)
    List<ApplicationRow> findByApplicantUserId(@Param("userId") long userId);

    @Select("""
            select application.id, application.application_no as applicationNo,
                   application.resident_id as residentId, resident.real_name as residentName,
                   application.applicant_user_id as applicantUserId,
                   application.grid_id as gridId, grid.area_name as gridName,
                   application.service_catalog_id as serviceCatalogId, catalog.service_name as serviceCatalogName,
                   application.request_content as requestContent, application.appointment_at as appointmentAt,
                   application.status, application.handler_user_id as handlerUserId,
                   handler.real_name as handlerName, application.result_summary as resultSummary,
                   application.rating, application.rating_remark as ratingRemark,
                   application.created_at as createdAt, application.completed_at as completedAt, application.version
            from service_application application
            join resident resident on resident.id = application.resident_id
            join grid_area grid on grid.id = application.grid_id
            join service_catalog catalog on catalog.id = application.service_catalog_id
            left join sys_user handler on handler.id = application.handler_user_id
            where application.id = #{id}
            """)
    Optional<ApplicationRow> findById(@Param("id") long id);

    @Select("""
            select id, resident_id as residentId, applicant_user_id as applicantUserId, grid_id as gridId,
                   status, handler_user_id as handlerUserId, rating, version
            from service_application where id = #{id} for update
            """)
    ApplicationLockRow findByIdForUpdate(@Param("id") long id);

    @Select("""
            select application.id, application.application_no as applicationNo,
                   application.resident_id as residentId, resident.real_name as residentName,
                   application.applicant_user_id as applicantUserId,
                   application.grid_id as gridId, grid.area_name as gridName,
                   application.service_catalog_id as serviceCatalogId, catalog.service_name as serviceCatalogName,
                   application.request_content as requestContent, application.appointment_at as appointmentAt,
                   application.status, application.handler_user_id as handlerUserId,
                   handler.real_name as handlerName, application.result_summary as resultSummary,
                   application.rating, application.rating_remark as ratingRemark,
                   application.created_at as createdAt, application.completed_at as completedAt, application.version
            from service_application application
            join resident resident on resident.id = application.resident_id
            join grid_area grid on grid.id = application.grid_id
            join service_catalog catalog on catalog.id = application.service_catalog_id
            left join sys_user handler on handler.id = application.handler_user_id
            where application.applicant_user_id = #{userId} and application.request_token = #{requestToken}
            limit 1
            """)
    ApplicationRow findByApplicantAndRequestToken(
            @Param("userId") long userId,
            @Param("requestToken") String requestToken
    );

    @Insert("""
            insert into service_application
              (application_no, resident_id, applicant_user_id, grid_id, service_catalog_id,
               request_content, appointment_at, request_token, status)
            values
              (#{applicationNo}, #{residentId}, #{applicantUserId}, #{gridId}, #{serviceCatalogId},
               #{requestContent}, #{appointmentAt}, #{requestToken}, 'SUBMITTED')
            """)
    int insert(
            @Param("applicationNo") String applicationNo,
            @Param("residentId") long residentId,
            @Param("applicantUserId") long applicantUserId,
            @Param("gridId") long gridId,
            @Param("serviceCatalogId") long serviceCatalogId,
            @Param("requestContent") String requestContent,
            @Param("appointmentAt") LocalDateTime appointmentAt,
            @Param("requestToken") String requestToken
    );

    @Select("select id from service_application where application_no = #{applicationNo}")
    Long findIdByApplicationNo(@Param("applicationNo") String applicationNo);

    @Update("""
            update service_application
            set status = #{toStatus},
                handler_user_id = case when #{handlerUserId} is null then handler_user_id else #{handlerUserId} end,
                result_summary = case when #{resultSummary} is null then result_summary else #{resultSummary} end,
                accepted_at = case when #{toStatus} = 'ACCEPTED' then current_timestamp(3) else accepted_at end,
                started_at = case when #{toStatus} = 'PROCESSING' then current_timestamp(3) else started_at end,
                completed_at = case when #{toStatus} = 'COMPLETED' then current_timestamp(3) else completed_at end,
                rejected_at = case when #{toStatus} = 'REJECTED' then current_timestamp(3) else rejected_at end,
                cancelled_at = case when #{toStatus} = 'CANCELLED' then current_timestamp(3) else cancelled_at end,
                version = version + 1
            where id = #{id} and status = #{fromStatus} and version = #{version}
            """)
    int transition(
            @Param("id") long id,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("handlerUserId") Long handlerUserId,
            @Param("resultSummary") String resultSummary,
            @Param("version") int version
    );

    @Update("""
            update service_application
            set rating = #{rating}, rating_remark = #{ratingRemark}, rated_at = current_timestamp(3), version = version + 1
            where id = #{id} and applicant_user_id = #{applicantUserId}
              and status = 'COMPLETED' and rating is null and version = #{version}
            """)
    int rate(
            @Param("id") long id,
            @Param("applicantUserId") long applicantUserId,
            @Param("rating") int rating,
            @Param("ratingRemark") String ratingRemark,
            @Param("version") int version
    );

    @Select("select count(*) from service_application where grid_id = #{gridId} and status not in ('COMPLETED', 'REJECTED', 'CANCELLED')")
    int countOpenByGridId(@Param("gridId") long gridId);

    @Select("select count(*) from service_application where handler_user_id = #{userId} and status not in ('COMPLETED', 'REJECTED', 'CANCELLED')")
    int countOpenByHandlerUserId(@Param("userId") long userId);

    record ApplicationRow(
            Long id, String applicationNo, Long residentId, String residentName, Long applicantUserId,
            Long gridId, String gridName, Long serviceCatalogId, String serviceCatalogName,
            String requestContent, LocalDateTime appointmentAt, String status, Long handlerUserId,
            String handlerName, String resultSummary, Integer rating, String ratingRemark,
            LocalDateTime createdAt, LocalDateTime completedAt, int version
    ) {
    }

    record ApplicationLockRow(Long id, Long residentId, Long applicantUserId, Long gridId,
                              String status, Long handlerUserId, Integer rating, int version) {
    }
}
