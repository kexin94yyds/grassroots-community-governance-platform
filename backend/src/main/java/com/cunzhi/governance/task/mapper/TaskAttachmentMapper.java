package com.cunzhi.governance.task.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskAttachmentMapper {

    @Insert("""
            insert into task_attachment
              (task_id, storage_key, original_name, content_type, file_size, sha256, upload_token, uploaded_by)
            values
              (#{taskId}, #{storageKey}, #{originalName}, #{contentType}, #{fileSize}, #{sha256}, #{uploadToken}, #{uploadedBy})
            """)
    int insert(
            @Param("taskId") long taskId,
            @Param("storageKey") String storageKey,
            @Param("originalName") String originalName,
            @Param("contentType") String contentType,
            @Param("fileSize") long fileSize,
            @Param("sha256") String sha256,
            @Param("uploadToken") String uploadToken,
            @Param("uploadedBy") long uploadedBy
    );

    @Select("select id from task_attachment where storage_key = #{storageKey}")
    Long findIdByStorageKey(@Param("storageKey") String storageKey);

    @Select("""
            select a.id, a.task_id as taskId, t.grid_id as gridId, a.storage_key as storageKey,
                   a.original_name as originalName, a.content_type as contentType, a.file_size as fileSize,
                   a.sha256, a.upload_token as uploadToken, a.uploaded_by as uploadedBy, coalesce(u.real_name, u.username) as uploaderName,
                   a.created_at as createdAt
            from task_attachment a
            join work_task t on t.id = a.task_id
            left join sys_user u on u.id = a.uploaded_by
            where a.id = #{id} and a.status = 'ACTIVE'
            """)
    AttachmentRow findById(@Param("id") long id);

    @Select("""
            select a.id, a.task_id as taskId, t.grid_id as gridId, a.storage_key as storageKey,
                   a.original_name as originalName, a.content_type as contentType, a.file_size as fileSize,
                   a.sha256, a.upload_token as uploadToken, a.uploaded_by as uploadedBy, coalesce(u.real_name, u.username) as uploaderName,
                   a.created_at as createdAt
            from task_attachment a
            join work_task t on t.id = a.task_id
            left join sys_user u on u.id = a.uploaded_by
            where a.id = #{id} and a.status = 'ACTIVE'
            for update
            """)
    AttachmentRow findActiveByIdForUpdate(@Param("id") long id);

    @Select("""
            select a.id, a.task_id as taskId, t.grid_id as gridId, a.storage_key as storageKey,
                   a.original_name as originalName, a.content_type as contentType, a.file_size as fileSize,
                   a.sha256, a.upload_token as uploadToken, a.uploaded_by as uploadedBy,
                   coalesce(u.real_name, u.username) as uploaderName, a.created_at as createdAt
            from task_attachment a
            join work_task t on t.id = a.task_id
            left join sys_user u on u.id = a.uploaded_by
            where a.task_id = #{taskId}
              and a.uploaded_by = #{uploadedBy}
              and a.upload_token = #{uploadToken}
              and a.status = 'ACTIVE'
            limit 1
            """)
    AttachmentRow findActiveByTaskAndUploaderAndUploadToken(
            @Param("taskId") long taskId,
            @Param("uploadedBy") long uploadedBy,
            @Param("uploadToken") String uploadToken
    );

    @Select("""
            select a.id, a.task_id as taskId, t.grid_id as gridId, a.storage_key as storageKey,
                   a.original_name as originalName, a.content_type as contentType, a.file_size as fileSize,
                   a.sha256, a.upload_token as uploadToken, a.uploaded_by as uploadedBy, coalesce(u.real_name, u.username) as uploaderName,
                   a.created_at as createdAt
            from task_attachment a
            join work_task t on t.id = a.task_id
            left join sys_user u on u.id = a.uploaded_by
            where a.task_id = #{taskId} and a.status = 'ACTIVE'
            order by a.created_at, a.id
            """)
    List<AttachmentRow> findByTaskId(@Param("taskId") long taskId);

    @Select("select count(*) from task_attachment where task_id = #{taskId} and status = 'ACTIVE'")
    int countActiveByTaskId(@Param("taskId") long taskId);

    @Select("""
            select id, storage_key as storageKey
            from task_attachment
            where status = 'DELETED' and file_purged_at is null
            order by deleted_at, id
            limit #{limit}
            """)
    List<PendingFilePurgeRow> findPendingFilePurges(@Param("limit") int limit);

    @Select("""
            <script>
            select count(*) from task_attachment
            where task_id = #{taskId} and status = 'ACTIVE'
              and id in
              <foreach collection="attachmentIds" item="attachmentId" open="(" separator="," close=")">
                #{attachmentId}
              </foreach>
            </script>
            """)
    int countActiveByTaskAndIds(
            @Param("taskId") long taskId,
            @Param("attachmentIds") List<Long> attachmentIds
    );

    @Update("""
            update task_attachment
            set status = 'DELETED', deleted_by = #{deletedBy}, deleted_at = current_timestamp(3),
                version = version + 1
            where id = #{id} and status = 'ACTIVE'
            """)
    int softDelete(@Param("id") long id, @Param("deletedBy") long deletedBy);

    @Update("""
            update task_attachment
            set file_purged_at = current_timestamp(3)
            where id = #{id} and status = 'DELETED' and file_purged_at is null
            """)
    int markFilePurged(@Param("id") long id);

    record PendingFilePurgeRow(Long id, String storageKey) {
    }

    record AttachmentRow(
            Long id, Long taskId, Long gridId, String storageKey, String originalName,
            String contentType, long fileSize, String sha256, String uploadToken, Long uploadedBy,
            String uploaderName, LocalDateTime createdAt
    ) {
    }
}
