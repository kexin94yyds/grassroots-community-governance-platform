package com.cunzhi.governance.task.service;

import com.cunzhi.governance.attachment.service.AttachmentFileStore;
import com.cunzhi.governance.attachment.service.AttachmentPurgeService;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.config.AppProperties;
import com.cunzhi.governance.system.security.RoleCodes;
import com.cunzhi.governance.system.service.DataScopeService;
import com.cunzhi.governance.task.dto.TaskAttachmentView;
import com.cunzhi.governance.task.mapper.TaskAttachmentMapper;
import com.cunzhi.governance.task.mapper.TaskMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskAttachmentService {

    private final TaskMapper taskMapper;
    private final TaskAttachmentMapper attachmentMapper;
    private final DataScopeService dataScopeService;
    private final AttachmentFileStore fileStore;
    private final AttachmentPurgeService purgeService;

    public TaskAttachmentService(
            TaskMapper taskMapper,
            TaskAttachmentMapper attachmentMapper,
            DataScopeService dataScopeService,
            AttachmentPurgeService purgeService,
            AppProperties appProperties
    ) {
        this.taskMapper = taskMapper;
        this.attachmentMapper = attachmentMapper;
        this.dataScopeService = dataScopeService;
        this.purgeService = purgeService;
        this.fileStore = new AttachmentFileStore(appProperties.attachment());
    }

    public List<TaskAttachmentView> findByTaskId(String taskIdValue) {
        long taskId = IdParser.parse(taskIdValue, "任务ID");
        TaskMapper.TaskRow task = requireTask(taskId);
        dataScopeService.requireGridAccess(task.gridId());
        return attachmentMapper.findByTaskId(taskId).stream().map(this::toView).toList();
    }

    @Transactional
    public TaskAttachmentView upload(String taskIdValue, MultipartFile file) {
        return upload(taskIdValue, file, null);
    }

    @Transactional
    public TaskAttachmentView upload(String taskIdValue, MultipartFile file, String requestToken) {
        long taskId = IdParser.parse(taskIdValue, "任务ID");
        TaskMapper.TaskRow task = requireTaskForUpdate(taskId);
        dataScopeService.requireGridAccess(task.gridId());
        long uploadedBy = dataScopeService.currentUser().id();
        String uploadToken = normalizeRequestToken(requestToken);
        TaskAttachmentMapper.AttachmentRow existing = findIdempotentAttachment(taskId, uploadedBy, uploadToken);
        if (existing != null) {
            return toView(existing);
        }
        if (!"PROCESSING".equals(task.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅处理中任务可以上传附件");
        }
        if (!task.assigneeUserId().equals(uploadedBy)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅任务执行人可以上传附件");
        }
        if (attachmentMapper.countActiveByTaskId(taskId) >= 20) {
            throw new BusinessException(ErrorCode.CONFLICT, "每个任务最多保留 20 个附件");
        }
        AttachmentFileStore.StoredFile stored = fileStore.store(file);
        try {
            if (attachmentMapper.insert(taskId, stored.storageKey(), stored.originalName(), stored.contentType(),
                    stored.fileSize(), stored.sha256(), uploadToken, uploadedBy) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件记录保存失败");
            }
            Long attachmentId = attachmentMapper.findIdByStorageKey(stored.storageKey());
            if (attachmentId == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件保存后未能读取记录");
            }
            registerRollbackCleanup(stored.path());
            return toView(requireAttachment(attachmentId));
        } catch (DuplicateKeyException exception) {
            fileStore.deleteQuietly(stored.path());
            throw new BusinessException(ErrorCode.CONFLICT, "上传请求令牌已被占用");
        } catch (RuntimeException exception) {
            fileStore.deleteQuietly(stored.path());
            throw exception;
        }
    }

    public AttachmentDownload download(String taskIdValue, String attachmentIdValue) {
        long taskId = IdParser.parse(taskIdValue, "任务ID");
        long attachmentId = IdParser.parse(attachmentIdValue, "附件ID");
        TaskMapper.TaskRow task = requireTask(taskId);
        dataScopeService.requireGridAccess(task.gridId());
        TaskAttachmentMapper.AttachmentRow row = requireAttachment(attachmentId);
        if (!row.taskId().equals(taskId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不属于该任务");
        }
        return new AttachmentDownload(toView(row), fileStore.requireExistingFile(row.storageKey()));
    }

    @Transactional
    public void delete(String taskIdValue, String attachmentIdValue) {
        long taskId = IdParser.parse(taskIdValue, "任务ID");
        long attachmentId = IdParser.parse(attachmentIdValue, "附件ID");
        TaskMapper.TaskRow task = requireTaskForUpdate(taskId);
        dataScopeService.requireGridAccess(task.gridId());
        if (!"PROCESSING".equals(task.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅处理中任务可以删除附件");
        }
        TaskAttachmentMapper.AttachmentRow row = requireAttachmentForUpdate(attachmentId);
        if (!row.taskId().equals(taskId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不属于该任务");
        }
        if (!isAttachmentManager() && !dataScopeService.currentUser().id().equals(row.uploadedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅上传者或管理人员可以删除附件");
        }
        if (attachmentMapper.softDelete(row.id(), dataScopeService.currentUser().id()) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "附件已被其他用户删除");
        }
        registerDeferredDeletion(row.id(), row.storageKey());
    }

    private TaskMapper.TaskRow requireTask(long taskId) {
        return taskMapper.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在"));
    }

    private TaskMapper.TaskRow requireTaskForUpdate(long taskId) {
        return taskMapper.findByIdForUpdate(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在"));
    }

    private TaskAttachmentMapper.AttachmentRow requireAttachment(long attachmentId) {
        TaskAttachmentMapper.AttachmentRow row = attachmentMapper.findById(attachmentId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        return row;
    }

    private TaskAttachmentMapper.AttachmentRow requireAttachmentForUpdate(long attachmentId) {
        TaskAttachmentMapper.AttachmentRow row = attachmentMapper.findActiveByIdForUpdate(attachmentId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        return row;
    }

    private boolean isAttachmentManager() {
        Set<String> roles = dataScopeService.currentUser().roles();
        return roles.contains(RoleCodes.SYSTEM_ADMIN) || roles.contains(RoleCodes.COMMUNITY_STAFF);
    }

    private TaskAttachmentMapper.AttachmentRow findIdempotentAttachment(
            long taskId,
            long uploadedBy,
            String uploadToken
    ) {
        if (uploadToken == null) {
            return null;
        }
        return attachmentMapper.findActiveByTaskAndUploaderAndUploadToken(taskId, uploadedBy, uploadToken);
    }

    private TaskAttachmentView toView(TaskAttachmentMapper.AttachmentRow row) {
        return new TaskAttachmentView(row.id().toString(), row.taskId().toString(), row.originalName(),
                row.contentType(), row.fileSize(), row.sha256(),
                row.uploadedBy() == null ? null : row.uploadedBy().toString(), row.uploaderName(), row.createdAt());
    }

    private void registerRollbackCleanup(Path stored) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    fileStore.deleteQuietly(stored);
                }
            }
        });
    }

    private void registerDeferredDeletion(long attachmentId, String storageKey) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            purgeService.purgeTaskAttachment(attachmentId, storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                purgeService.purgeTaskAttachment(attachmentId, storageKey);
            }
        });
    }

    private String normalizeRequestToken(String requestToken) {
        if (requestToken == null || requestToken.isBlank()) {
            return null;
        }
        if (!requestToken.equals(requestToken.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "requestToken 必须是标准 UUID");
        }
        String normalized = requestToken;
        if (!normalized.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "requestToken 必须是标准 UUID");
        }
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "requestToken 必须是标准 UUID");
        }
    }

    public record AttachmentDownload(TaskAttachmentView metadata, Path path) {
    }
}
