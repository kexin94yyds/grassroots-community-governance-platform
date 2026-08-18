package com.cunzhi.governance.attachment.service;

import com.cunzhi.governance.attachment.dto.EventAttachmentView;
import com.cunzhi.governance.attachment.mapper.EventAttachmentMapper;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.config.AppProperties;
import com.cunzhi.governance.event.mapper.EventMapper;
import com.cunzhi.governance.system.security.RoleCodes;
import com.cunzhi.governance.system.service.DataScopeService;
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
public class EventAttachmentService {

    private final EventAttachmentMapper attachmentMapper;
    private final EventMapper eventMapper;
    private final DataScopeService dataScopeService;
    private final AttachmentFileStore fileStore;
    private final AttachmentPurgeService purgeService;

    public EventAttachmentService(
            EventAttachmentMapper attachmentMapper,
            EventMapper eventMapper,
            DataScopeService dataScopeService,
            AttachmentPurgeService purgeService,
            AppProperties appProperties
    ) {
        this.attachmentMapper = attachmentMapper;
        this.eventMapper = eventMapper;
        this.dataScopeService = dataScopeService;
        this.purgeService = purgeService;
        this.fileStore = new AttachmentFileStore(appProperties.attachment());
    }

    @Transactional
    public EventAttachmentView upload(String eventIdValue, MultipartFile file) {
        return upload(eventIdValue, file, null);
    }

    @Transactional
    public EventAttachmentView upload(String eventIdValue, MultipartFile file, String requestToken) {
        long eventId = IdParser.parse(eventIdValue, "事件ID");
        EventMapper.EventOwnerRow event = requireEventForUpdate(eventId);
        dataScopeService.requireGridAccess(event.gridId());
        long uploadedBy = dataScopeService.currentUser().id();
        String uploadToken = normalizeRequestToken(requestToken);
        EventAttachmentMapper.AttachmentRow existing = findIdempotentAttachment(eventId, uploadedBy, uploadToken);
        if (existing != null) {
            return toView(existing);
        }
        requireAttachmentCapacity(eventId);
        return storeForEvent(eventId, file, uploadToken, uploadedBy);
    }

    public List<EventAttachmentView> findByEventId(String eventIdValue) {
        long eventId = IdParser.parse(eventIdValue, "事件ID");
        EventMapper.EventRow event = eventMapper.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "事件不存在"));
        dataScopeService.requireGridAccess(event.gridId());
        return attachmentMapper.findByEventId(eventId).stream().map(this::toView).toList();
    }

    public AttachmentDownload download(String attachmentIdValue) {
        long attachmentId = IdParser.parse(attachmentIdValue, "附件ID");
        EventAttachmentMapper.AttachmentRow row = requireAttachment(attachmentId);
        dataScopeService.requireGridAccess(row.gridId());
        return new AttachmentDownload(toView(row), fileStore.requireExistingFile(row.storageKey()));
    }

    @Transactional
    public void delete(String eventIdValue, String attachmentIdValue) {
        long eventId = IdParser.parse(eventIdValue, "事件ID");
        long attachmentId = IdParser.parse(attachmentIdValue, "附件ID");
        EventMapper.EventOwnerRow event = requireEventForUpdate(eventId);
        dataScopeService.requireGridAccess(event.gridId());
        if (!"REPORTED".equals(event.status()) && !"ACCEPTED".equals(event.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前事件状态不允许删除附件");
        }
        EventAttachmentMapper.AttachmentRow row = requireActiveAttachmentForUpdate(attachmentId);
        if (!row.eventId().equals(eventId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不属于该事件");
        }
        if (!isAttachmentManager() && !dataScopeService.currentUser().id().equals(row.uploadedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅上传者或管理人员可以删除附件");
        }
        deleteStoredAttachment(row);
    }

    public List<EventAttachmentView> findByResidentEvent(String eventIdValue) {
        long eventId = IdParser.parse(eventIdValue, "事件ID");
        requireResidentOwnedEvent(eventId);
        return attachmentMapper.findByEventId(eventId).stream().map(this::toView).toList();
    }

    @Transactional
    public EventAttachmentView uploadForResident(String eventIdValue, MultipartFile file) {
        return uploadForResident(eventIdValue, file, null);
    }

    @Transactional
    public EventAttachmentView uploadForResident(String eventIdValue, MultipartFile file, String requestToken) {
        long eventId = IdParser.parse(eventIdValue, "事件ID");
        EventMapper.EventOwnerRow event = requireResidentOwnedEventForUpdate(eventId);
        long uploadedBy = dataScopeService.currentUser().id();
        String uploadToken = normalizeRequestToken(requestToken);
        EventAttachmentMapper.AttachmentRow existing = findIdempotentAttachment(eventId, uploadedBy, uploadToken);
        if (existing != null) {
            return toView(existing);
        }
        if (!"REPORTED".equals(event.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅待受理事件可以上传附件");
        }
        requireAttachmentCapacity(eventId);
        return storeForEvent(eventId, file, uploadToken, uploadedBy);
    }

    public AttachmentDownload downloadForResident(String eventIdValue, String attachmentIdValue) {
        long eventId = IdParser.parse(eventIdValue, "事件ID");
        long attachmentId = IdParser.parse(attachmentIdValue, "附件ID");
        requireResidentOwnedEvent(eventId);
        EventAttachmentMapper.AttachmentRow row = requireAttachment(attachmentId);
        if (!row.eventId().equals(eventId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不属于该事件");
        }
        return new AttachmentDownload(toView(row), fileStore.requireExistingFile(row.storageKey()));
    }

    @Transactional
    public void deleteForResident(String eventIdValue, String attachmentIdValue) {
        long eventId = IdParser.parse(eventIdValue, "事件ID");
        long attachmentId = IdParser.parse(attachmentIdValue, "附件ID");
        EventMapper.EventOwnerRow event = requireResidentOwnedEventForUpdate(eventId);
        if (!"REPORTED".equals(event.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅待受理事件可以删除附件");
        }
        EventAttachmentMapper.AttachmentRow row = requireActiveAttachmentForUpdate(attachmentId);
        if (!row.eventId().equals(eventId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不属于该事件");
        }
        if (!dataScopeService.currentUser().id().equals(row.uploadedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "居民仅可删除本人上传的附件");
        }
        deleteStoredAttachment(row);
    }

    private EventAttachmentView storeForEvent(
            long eventId,
            MultipartFile file,
            String uploadToken,
            long uploadedBy
    ) {
        AttachmentFileStore.StoredFile stored = fileStore.store(file);
        try {
            int inserted = attachmentMapper.insert(
                    eventId, stored.storageKey(), stored.originalName(), stored.contentType(), stored.fileSize(),
                    stored.sha256(), uploadToken, uploadedBy
            );
            if (inserted != 1) {
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

    private EventMapper.EventOwnerRow requireEventForUpdate(long eventId) {
        EventMapper.EventOwnerRow event = eventMapper.findOwnerByIdForUpdate(eventId);
        if (event == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "事件不存在");
        }
        return event;
    }

    private EventMapper.EventOwnerRow requireResidentOwnedEvent(long eventId) {
        EventMapper.EventOwnerRow event = eventMapper.findOwnerById(eventId);
        if (event == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "事件不存在");
        }
        requireResidentOwnership(event);
        return event;
    }

    private EventMapper.EventOwnerRow requireResidentOwnedEventForUpdate(long eventId) {
        EventMapper.EventOwnerRow event = requireEventForUpdate(eventId);
        requireResidentOwnership(event);
        return event;
    }

    private void requireResidentOwnership(EventMapper.EventOwnerRow event) {
        if (event.reporterUserId() == null || !event.reporterUserId().equals(dataScopeService.currentUser().id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能访问本人上报的事件附件");
        }
    }

    private void requireAttachmentCapacity(long eventId) {
        if (attachmentMapper.countActiveByEventId(eventId) >= 20) {
            throw new BusinessException(ErrorCode.CONFLICT, "每个事件最多保留 20 个附件");
        }
    }

    private EventAttachmentMapper.AttachmentRow findIdempotentAttachment(
            long eventId,
            long uploadedBy,
            String uploadToken
    ) {
        if (uploadToken == null) {
            return null;
        }
        return attachmentMapper.findActiveByEventAndUploaderAndUploadToken(eventId, uploadedBy, uploadToken);
    }

    private EventAttachmentMapper.AttachmentRow requireAttachment(long id) {
        EventAttachmentMapper.AttachmentRow row = attachmentMapper.findById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        return row;
    }

    private EventAttachmentMapper.AttachmentRow requireActiveAttachmentForUpdate(long id) {
        EventAttachmentMapper.AttachmentRow row = attachmentMapper.findActiveByIdForUpdate(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        return row;
    }

    private boolean isAttachmentManager() {
        Set<String> roles = dataScopeService.currentUser().roles();
        return roles.contains(RoleCodes.SYSTEM_ADMIN) || roles.contains(RoleCodes.COMMUNITY_STAFF);
    }

    private void deleteStoredAttachment(EventAttachmentMapper.AttachmentRow row) {
        if (attachmentMapper.softDelete(row.id(), dataScopeService.currentUser().id()) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "附件已被其他用户删除");
        }
        registerDeferredDeletion(row.id(), row.storageKey());
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
            purgeService.purgeEventAttachment(attachmentId, storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                purgeService.purgeEventAttachment(attachmentId, storageKey);
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

    private EventAttachmentView toView(EventAttachmentMapper.AttachmentRow row) {
        return new EventAttachmentView(
                row.id().toString(), row.eventId().toString(), row.originalName(), row.contentType(),
                row.fileSize(), row.sha256(), row.uploadedBy() == null ? null : row.uploadedBy().toString(),
                row.uploaderName(), row.createdAt()
        );
    }

    public record AttachmentDownload(EventAttachmentView metadata, Path path) {
    }
}
