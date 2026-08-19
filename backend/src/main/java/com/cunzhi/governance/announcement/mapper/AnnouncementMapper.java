package com.cunzhi.governance.announcement.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnnouncementMapper {

    @Select("""
            <script>
            select a.id, a.announcement_no as announcementNo, a.audience_scope as audienceScope,
                   a.community_id as communityId, community.area_name as communityName,
                   a.title, a.content, a.pinned, a.status, a.created_by as createdBy,
                   creator.real_name as createdByName, a.published_at as publishedAt,
                   a.withdrawn_at as withdrawnAt, a.created_at as createdAt, a.version
            from community_announcement a
            left join grid_area community on community.id = a.community_id
            join sys_user creator on creator.id = a.created_by
            where (
              (a.status = 'PUBLISHED' and (
                   a.audience_scope = 'GLOBAL'
                   <if test="allAccess">or a.audience_scope = 'COMMUNITY'</if>
                   <if test="!allAccess and gridIds != null and gridIds.size() > 0">
                     or a.community_id in (
                       select distinct parent_id from grid_area
                       where id in
                       <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
                         and area_type = 'GRID' and parent_id is not null
                     )
                   </if>
              ))
              <if test="includeNonPublished">
                or (
                  <choose>
                    <when test="allAccess">1 = 1</when>
                    <when test="gridIds != null and gridIds.size() > 0">
                      a.audience_scope = 'COMMUNITY' and a.community_id in (
                        select distinct parent_id from grid_area
                        where id in
                        <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">#{gridId}</foreach>
                          and area_type = 'GRID' and parent_id is not null
                      )
                    </when>
                    <otherwise>1 = 0</otherwise>
                  </choose>
                )
              </if>
            )
            order by a.pinned desc, a.published_at desc, a.created_at desc, a.id desc
            limit 100
            </script>
            """)
    List<AnnouncementRow> findVisible(
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds,
            @Param("includeNonPublished") boolean includeNonPublished
    );

    @Select("""
            select a.id, a.announcement_no as announcementNo, a.audience_scope as audienceScope,
                   a.community_id as communityId, community.area_name as communityName,
                   a.title, a.content, a.pinned, a.status, a.created_by as createdBy,
                   creator.real_name as createdByName, a.published_at as publishedAt,
                   a.withdrawn_at as withdrawnAt, a.created_at as createdAt, a.version
            from community_announcement a
            left join grid_area community on community.id = a.community_id
            join sys_user creator on creator.id = a.created_by
            where a.id = #{id}
            """)
    Optional<AnnouncementRow> findById(@Param("id") long id);

    @Select("""
            select id, audience_scope as audienceScope, community_id as communityId, status, version, created_by as createdBy
            from community_announcement where id = #{id} for update
            """)
    AnnouncementLockRow findLockById(@Param("id") long id);

    @Insert("""
            insert into community_announcement
              (announcement_no, audience_scope, community_id, title, content, pinned, status, created_by)
            values
              (#{announcementNo}, #{audienceScope}, #{communityId}, #{title}, #{content}, #{pinned}, 'DRAFT', #{createdBy})
            """)
    int insert(
            @Param("announcementNo") String announcementNo,
            @Param("audienceScope") String audienceScope,
            @Param("communityId") Long communityId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("pinned") boolean pinned,
            @Param("createdBy") long createdBy
    );

    @Select("select id from community_announcement where announcement_no = #{announcementNo}")
    Long findIdByAnnouncementNo(@Param("announcementNo") String announcementNo);

    @Update("""
            update community_announcement
            set title = #{title}, content = #{content}, pinned = #{pinned}, version = version + 1
            where id = #{id} and status = 'DRAFT' and version = #{version}
            """)
    int updateDraft(
            @Param("id") long id,
            @Param("title") String title,
            @Param("content") String content,
            @Param("pinned") boolean pinned,
            @Param("version") int version
    );

    @Update("""
            update community_announcement
            set status = #{toStatus},
                published_by = case when #{toStatus} = 'PUBLISHED' then #{operatorUserId} else published_by end,
                published_at = case when #{toStatus} = 'PUBLISHED' then current_timestamp(3) else published_at end,
                withdrawn_by = case when #{toStatus} = 'WITHDRAWN' then #{operatorUserId} else withdrawn_by end,
                withdrawn_at = case when #{toStatus} = 'WITHDRAWN' then current_timestamp(3) else withdrawn_at end,
                version = version + 1
            where id = #{id} and status = #{fromStatus} and version = #{version}
            """)
    int transition(
            @Param("id") long id,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("operatorUserId") long operatorUserId,
            @Param("version") int version
    );

    record AnnouncementRow(
            Long id, String announcementNo, String audienceScope, Long communityId, String communityName,
            String title, String content, boolean pinned, String status, Long createdBy, String createdByName,
            LocalDateTime publishedAt, LocalDateTime withdrawnAt, LocalDateTime createdAt, int version
    ) {
    }

    record AnnouncementLockRow(Long id, String audienceScope, Long communityId, String status, int version, Long createdBy) {
    }
}
