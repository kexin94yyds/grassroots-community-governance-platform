package com.cunzhi.governance.attachment.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface EventAttachmentMapper {

    @Insert("""
            insert into event_attachment
              (event_id, storage_key, original_name, content_type, file_size, sha256, upload_token, uploaded_by)
            values
              (#{eventId}, #{storageKey}, #{originalName}, #{contentType}, #{fileSize}, #{sha256}, #{uploadToken}, #{uploadedBy})
            """)
    int insert(
            @Param("eventId") long eventId,
            @Param("storageKey") String storageKey,
            @Param("originalName") String originalName,
            @Param("contentType") String contentType,
            @Param("fileSize") long fileSize,
            @Param("sha256") String sha256,
            @Param("uploadToken") String uploadToken,
            @Param("uploadedBy") long uploadedBy
    );

    @Select("select id from event_attachment where storage_key = #{storageKey}")
    Long findIdByStorageKey(@Param("storageKey") String storageKey);

    @Select("""
            select attachment.id, attachment.event_id as eventId, event.grid_id as gridId,
                   attachment.storage_key as storageKey,
                   attachment.original_name as originalName,
                   attachment.content_type as contentType,
                   attachment.file_size as fileSize, attachment.sha256, attachment.upload_token as uploadToken,
                   attachment.uploaded_by as uploadedBy, uploader.real_name as uploaderName,
                   attachment.created_at as createdAt
            from event_attachment attachment
            join governance_event event on event.id = attachment.event_id
            left join sys_user uploader on uploader.id = attachment.uploaded_by
            where attachment.id = #{id} and attachment.status = 'ACTIVE'
            """)
    AttachmentRow findById(@Param("id") long id);

    @Select("""
            select attachment.id, attachment.event_id as eventId, event.grid_id as gridId,
                   attachment.storage_key as storageKey,
                   attachment.original_name as originalName,
                   attachment.content_type as contentType,
                   attachment.file_size as fileSize, attachment.sha256, attachment.upload_token as uploadToken,
                   attachment.uploaded_by as uploadedBy, uploader.real_name as uploaderName,
                   attachment.created_at as createdAt
            from event_attachment attachment
            join governance_event event on event.id = attachment.event_id
            left join sys_user uploader on uploader.id = attachment.uploaded_by
            where attachment.id = #{id} and attachment.status = 'ACTIVE'
            for update
            """)
    AttachmentRow findActiveByIdForUpdate(@Param("id") long id);

    @Select("""
            select attachment.id, attachment.event_id as eventId, event.grid_id as gridId,
                   attachment.storage_key as storageKey,
                   attachment.original_name as originalName,
                   attachment.content_type as contentType,
                   attachment.file_size as fileSize, attachment.sha256, attachment.upload_token as uploadToken,
                   attachment.uploaded_by as uploadedBy, uploader.real_name as uploaderName,
                   attachment.created_at as createdAt
            from event_attachment attachment
            join governance_event event on event.id = attachment.event_id
            left join sys_user uploader on uploader.id = attachment.uploaded_by
            where attachment.event_id = #{eventId}
              and attachment.uploaded_by = #{uploadedBy}
              and attachment.upload_token = #{uploadToken}
              and attachment.status = 'ACTIVE'
            limit 1
            """)
    AttachmentRow findActiveByEventAndUploaderAndUploadToken(
            @Param("eventId") long eventId,
            @Param("uploadedBy") long uploadedBy,
            @Param("uploadToken") String uploadToken
    );

    @Select("""
            select attachment.id, attachment.event_id as eventId, event.grid_id as gridId,
                   attachment.storage_key as storageKey,
                   attachment.original_name as originalName,
                   attachment.content_type as contentType,
                   attachment.file_size as fileSize, attachment.sha256, attachment.upload_token as uploadToken,
                   attachment.uploaded_by as uploadedBy, uploader.real_name as uploaderName,
                   attachment.created_at as createdAt
            from event_attachment attachment
            join governance_event event on event.id = attachment.event_id
            left join sys_user uploader on uploader.id = attachment.uploaded_by
            where attachment.event_id = #{eventId} and attachment.status = 'ACTIVE'
            order by attachment.created_at, attachment.id
            """)
    List<AttachmentRow> findByEventId(@Param("eventId") long eventId);

    @Select("select count(*) from event_attachment where event_id = #{eventId} and status = 'ACTIVE'")
    int countActiveByEventId(@Param("eventId") long eventId);

    @Select("""
            select id, storage_key as storageKey
            from event_attachment
            where status = 'DELETED' and file_purged_at is null
            order by deleted_at, id
            limit #{limit}
            """)
    List<PendingFilePurgeRow> findPendingFilePurges(@Param("limit") int limit);

    @Update("""
            update event_attachment
            set status = 'DELETED', deleted_by = #{deletedBy}, deleted_at = current_timestamp(3),
                version = version + 1
            where id = #{id} and status = 'ACTIVE'
            """)
    int softDelete(@Param("id") long id, @Param("deletedBy") long deletedBy);

    @Update("""
            update event_attachment
            set file_purged_at = current_timestamp(3)
            where id = #{id} and status = 'DELETED' and file_purged_at is null
            """)
    int markFilePurged(@Param("id") long id);

    record PendingFilePurgeRow(Long id, String storageKey) {
    }

    record AttachmentRow(
            Long id,
            Long eventId,
            Long gridId,
            String storageKey,
            String originalName,
            String contentType,
            long fileSize,
            String sha256,
            String uploadToken,
            Long uploadedBy,
            String uploaderName,
            LocalDateTime createdAt
    ) {
    }
}
