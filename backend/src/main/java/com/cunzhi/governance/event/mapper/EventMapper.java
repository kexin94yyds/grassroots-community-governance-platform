package com.cunzhi.governance.event.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventMapper {

    @Select("""
            select id, category_code as code, category_name as name
            from event_category
            where status = 'ENABLED'
            order by sort_no, id
            """)
    List<EventCategoryRow> findEnabledCategories();

    @Select("""
            select id from event_category
            where id = #{categoryId} and status = 'ENABLED'
            for update
            """)
    Long lockEnabledCategory(@Param("categoryId") long categoryId);

    @Select("""
            <script>
            select count(*)
            from governance_event e
            where 1 = 1
              <if test="keyword != null and keyword != ''">
                and (e.event_no like concat('%', #{keyword}, '%')
                     or e.title like concat('%', #{keyword}, '%')
                     or e.address like concat('%', #{keyword}, '%'))
              </if>
              <if test="status != null and status != ''">
                and e.status = #{status}
              </if>
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
            select e.id, e.event_no as eventNo, e.category_id as categoryId,
                   category.category_name as categoryName,
                   e.grid_id as gridId, grid.area_name as gridName,
                   e.title, e.description, e.address,
                   e.report_channel as reportChannel, e.severity, e.status,
                   e.assigned_to_user_id as assignedToUserId,
                   coalesce(assignee.real_name, assignee.username) as assignedToName,
                   e.result_summary as resultSummary, e.reported_at as reportedAt,
                   e.version
            from governance_event e
            join event_category category on category.id = e.category_id
            join grid_area grid on grid.id = e.grid_id
            left join sys_user assignee on assignee.id = e.assigned_to_user_id
            where 1 = 1
              <if test="keyword != null and keyword != ''">
                and (e.event_no like concat('%', #{keyword}, '%')
                     or e.title like concat('%', #{keyword}, '%')
                     or e.address like concat('%', #{keyword}, '%'))
              </if>
              <if test="status != null and status != ''">
                and e.status = #{status}
              </if>
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
            limit #{size} offset #{offset}
            </script>
            """)
    List<EventRow> findPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            select e.id, e.event_no as eventNo, e.category_id as categoryId,
                   category.category_name as categoryName,
                   e.grid_id as gridId, grid.area_name as gridName,
                   e.title, e.description, e.address,
                   e.report_channel as reportChannel, e.severity, e.status,
                   e.assigned_to_user_id as assignedToUserId,
                   coalesce(assignee.real_name, assignee.username) as assignedToName,
                   e.result_summary as resultSummary, e.reported_at as reportedAt, e.version
            from governance_event e
            join event_category category on category.id = e.category_id
            join grid_area grid on grid.id = e.grid_id
            left join sys_user assignee on assignee.id = e.assigned_to_user_id
            where e.id = #{id}
            """)
    Optional<EventRow> findById(@Param("id") long id);

    @Select("""
            select id, grid_id as gridId, reporter_user_id as reporterUserId, status
            from governance_event where id = #{id}
            """)
    EventOwnerRow findOwnerById(@Param("id") long id);

    @Select("""
            select id, grid_id as gridId, reporter_user_id as reporterUserId, status
            from governance_event where id = #{id}
            for update
            """)
    EventOwnerRow findOwnerByIdForUpdate(@Param("id") long id);

    @Select("""
            select e.id, e.event_no as eventNo, e.category_id as categoryId,
                   category.category_name as categoryName,
                   e.grid_id as gridId, grid.area_name as gridName,
                   e.title, e.description, e.address,
                   e.report_channel as reportChannel, e.severity, e.status,
                   e.assigned_to_user_id as assignedToUserId,
                   coalesce(assignee.real_name, assignee.username) as assignedToName,
                   e.result_summary as resultSummary, e.reported_at as reportedAt, e.version
            from governance_event e
            join event_category category on category.id = e.category_id
            join grid_area grid on grid.id = e.grid_id
            left join sys_user assignee on assignee.id = e.assigned_to_user_id
            where e.reporter_user_id = #{userId}
            order by e.reported_at desc, e.id desc
            limit 100
            """)
    List<EventRow> findByReporterUserId(@Param("userId") long userId);

    @Insert("""
            insert into governance_event
              (event_no, category_id, grid_id, title, description, report_channel, severity,
               status, address, reporter_user_id, reporter_name)
            values
              (#{eventNo}, #{categoryId}, #{gridId}, #{title}, #{description}, #{reportChannel},
               #{severity}, 'REPORTED', #{address}, #{reporterUserId}, #{reporterName})
            """)
    int insert(
            @Param("eventNo") String eventNo,
            @Param("categoryId") long categoryId,
            @Param("gridId") long gridId,
            @Param("title") String title,
            @Param("description") String description,
            @Param("reportChannel") String reportChannel,
            @Param("severity") String severity,
            @Param("address") String address,
            @Param("reporterUserId") long reporterUserId,
            @Param("reporterName") String reporterName
    );

    @Select("select id from governance_event where event_no = #{eventNo}")
    long findIdByEventNo(@Param("eventNo") String eventNo);

    @Update("""
            update governance_event
            set status = #{toStatus},
                assigned_to_user_id = coalesce(#{assignedToUserId}, assigned_to_user_id),
                result_summary = coalesce(#{resultSummary}, result_summary),
                accepted_at = case when #{toStatus} = 'ACCEPTED' then current_timestamp(3) else accepted_at end,
                closed_at = case when #{toStatus} in ('CLOSED', 'REJECTED', 'CANCELLED')
                                 then current_timestamp(3) else closed_at end,
                version = version + 1
            where id = #{id} and status = #{fromStatus} and version = #{version}
            """)
    int transition(
            @Param("id") long id,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("version") int version,
            @Param("assignedToUserId") Long assignedToUserId,
            @Param("resultSummary") String resultSummary
    );

    record EventRow(
            Long id,
            String eventNo,
            Long categoryId,
            String categoryName,
            Long gridId,
            String gridName,
            String title,
            String description,
            String address,
            String reportChannel,
            String severity,
            String status,
            Long assignedToUserId,
            String assignedToName,
            String resultSummary,
            LocalDateTime reportedAt,
            int version
    ) {
    }

    record EventCategoryRow(Long id, String code, String name) {
    }

    record EventOwnerRow(Long id, Long gridId, Long reporterUserId, String status) {
    }
}
