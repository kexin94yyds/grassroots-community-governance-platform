package com.cunzhi.governance.attachment.service;

import com.cunzhi.governance.attachment.mapper.EventAttachmentMapper;
import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.config.AppProperties;
import com.cunzhi.governance.event.mapper.EventMapper;
import com.cunzhi.governance.system.service.DataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventAttachmentServiceTest {

    @TempDir
    Path storageRoot;

    @Mock
    private EventAttachmentMapper attachmentMapper;
    @Mock
    private EventMapper eventMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private AttachmentPurgeService purgeService;

    @Test
    void storesVerifiedFileWithRandomKeyAndSha256() throws Exception {
        byte[] content = jpegBytes();
        when(eventMapper.findOwnerByIdForUpdate(7)).thenReturn(lockedEvent());
        when(dataScopeService.currentUser()).thenReturn(user());
        when(attachmentMapper.insert(eq(7L), anyString(), eq("现场照片.jpg"), eq("image/jpeg"),
                eq((long) content.length), anyString(), isNull(), eq(5L))).thenReturn(1);
        when(attachmentMapper.findIdByStorageKey(anyString())).thenReturn(11L);
        when(attachmentMapper.findById(11)).thenReturn(row("00000000-0000-0000-0000-000000000011"));

        var result = service().upload(
                "7",
                new MockMultipartFile("file", "现场照片.jpg", "image/jpeg", content)
        );

        assertThat(result.id()).isEqualTo("11");
        assertThat(result.originalName()).isEqualTo("现场照片.jpg");
        assertThat(Files.list(storageRoot).filter(Files::isRegularFile).count()).isEqualTo(1);
        verify(dataScopeService).requireGridAccess(9);
    }

    @Test
    void rejectsSpoofedMimeTypeAndRemovesTemporaryFile() throws Exception {
        when(eventMapper.findOwnerByIdForUpdate(7)).thenReturn(lockedEvent());
        when(dataScopeService.currentUser()).thenReturn(user());

        assertThatThrownBy(() -> service().upload(
                "7",
                new MockMultipartFile("file", "伪装.jpg", "image/jpeg", pngBytes())
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThat(Files.list(storageRoot).findAny()).isEmpty();
        verify(attachmentMapper, never()).insert(
                eq(7L), anyString(), anyString(), anyString(), eq((long) pngBytes().length), anyString(), isNull(), eq(5L)
        );
    }

    @Test
    void removesStoredFileWhenDatabaseInsertFails() throws Exception {
        byte[] content = jpegBytes();
        when(eventMapper.findOwnerByIdForUpdate(7)).thenReturn(lockedEvent());
        when(dataScopeService.currentUser()).thenReturn(user());
        when(attachmentMapper.insert(eq(7L), anyString(), anyString(), anyString(),
                eq((long) content.length), anyString(), isNull(), eq(5L)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service().upload(
                "7",
                new MockMultipartFile("file", "现场照片.jpg", "image/jpeg", content)
        )).isInstanceOf(IllegalStateException.class);

        assertThat(Files.list(storageRoot).findAny()).isEmpty();
    }

    @Test
    void checksEventGridScopeBeforeReturningDownloadPath() throws Exception {
        String storageKey = UUID.randomUUID().toString();
        Files.write(storageRoot.resolve(storageKey), jpegBytes());
        when(attachmentMapper.findById(11)).thenReturn(row(storageKey));

        var result = service().download("11");

        assertThat(Files.isSameFile(result.path(), storageRoot.resolve(storageKey))).isTrue();
        verify(dataScopeService).requireGridAccess(9);
    }

    @Test
    void rejectsTwentyFirstActiveAttachmentBeforeStoringFile() throws Exception {
        when(eventMapper.findOwnerByIdForUpdate(7)).thenReturn(lockedEvent());
        when(dataScopeService.currentUser()).thenReturn(user());
        when(attachmentMapper.countActiveByEventId(7L)).thenReturn(20);

        assertThatThrownBy(() -> service().upload(
                "7", new MockMultipartFile("file", "第21个.jpg", "image/jpeg", jpegBytes())
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        assertThat(Files.list(storageRoot).findAny()).isEmpty();
        verify(attachmentMapper, never()).insert(
                eq(7L), anyString(), anyString(), anyString(), eq((long) jpegBytes().length), anyString(), isNull(), eq(5L)
        );
    }

    @Test
    void rejectsDeleteWhenEventIsNoLongerInAnAllowedState() {
        when(eventMapper.findOwnerByIdForUpdate(7)).thenReturn(new EventMapper.EventOwnerRow(7L, 9L, 5L, "CLOSED"));

        assertThatThrownBy(() -> service().delete("7", "11"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(attachmentMapper, never()).findActiveByIdForUpdate(11L);
    }

    @Test
    void residentCannotDeleteStaffAttachmentEvenOnOwnReportedEvent() {
        when(eventMapper.findOwnerByIdForUpdate(7)).thenReturn(new EventMapper.EventOwnerRow(7L, 9L, 5L, "REPORTED"));
        when(dataScopeService.currentUser()).thenReturn(residentUser());
        when(attachmentMapper.findActiveByIdForUpdate(11L)).thenReturn(row("staff-upload", 9L));

        assertThatThrownBy(() -> service().deleteForResident("7", "11"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(attachmentMapper, never()).softDelete(11L, 5L);
    }

    @Test
    void residentCannotReachAnotherResidentsEventAttachment() {
        when(eventMapper.findOwnerById(7)).thenReturn(new EventMapper.EventOwnerRow(7L, 9L, 8L, "REPORTED"));
        when(dataScopeService.currentUser()).thenReturn(residentUser());

        assertThatThrownBy(() -> service().findByResidentEvent("7"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(attachmentMapper, never()).findByEventId(7L);
    }

    @Test
    void returnsExistingAttachmentForRepeatedActiveRequestTokenWithoutStoringFile() throws Exception {
        String token = "123e4567-e89b-12d3-a456-426614174000";
        when(eventMapper.findOwnerByIdForUpdate(7)).thenReturn(lockedEvent());
        when(dataScopeService.currentUser()).thenReturn(user());
        when(attachmentMapper.findActiveByEventAndUploaderAndUploadToken(7L, 5L, token))
                .thenReturn(row("00000000-0000-0000-0000-000000000011", token, 5L));

        var result = service().upload(
                "7", new MockMultipartFile("file", "重试.jpg", "image/jpeg", jpegBytes()), token
        );

        assertThat(result.id()).isEqualTo("11");
        assertThat(Files.list(storageRoot).findAny()).isEmpty();
        verify(attachmentMapper, never()).countActiveByEventId(7L);
        verify(attachmentMapper, never()).insert(
                eq(7L), anyString(), anyString(), anyString(), eq((long) jpegBytes().length), anyString(), eq(token), eq(5L)
        );
    }

    @Test
    void rejectsNonCanonicalRequestTokenBeforeStoringFile() throws Exception {
        when(eventMapper.findOwnerByIdForUpdate(7L)).thenReturn(lockedEvent());
        when(dataScopeService.currentUser()).thenReturn(user());

        assertThatThrownBy(() -> service().upload(
                "7", new MockMultipartFile("file", "重试.jpg", "image/jpeg", jpegBytes()),
                " 123e4567-e89b-12d3-a456-426614174000 "
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(attachmentMapper, never()).countActiveByEventId(7L);
        assertThat(Files.list(storageRoot).findAny()).isEmpty();
    }

    @Test
    void residentRetryReturnsExistingAttachmentBeforeReportedStateCheck() throws Exception {
        String token = "123e4567-e89b-12d3-a456-426614174000";
        when(eventMapper.findOwnerByIdForUpdate(7L))
                .thenReturn(new EventMapper.EventOwnerRow(7L, 9L, 5L, "ACCEPTED"));
        when(dataScopeService.currentUser()).thenReturn(residentUser());
        when(attachmentMapper.findActiveByEventAndUploaderAndUploadToken(7L, 5L, token))
                .thenReturn(row("00000000-0000-0000-0000-000000000011", token, 5L));

        var result = service().uploadForResident(
                "7", new MockMultipartFile("file", "重试.jpg", "image/jpeg", jpegBytes()), token
        );

        assertThat(result.id()).isEqualTo("11");
        assertThat(Files.list(storageRoot).findAny()).isEmpty();
        verify(attachmentMapper, never()).countActiveByEventId(7L);
    }

    @Test
    void directDeleteSoftDeletesBeforeRemovingOriginalFileWithoutStaging() throws Exception {
        String storageKey = "00000000-0000-0000-0000-000000000019";
        when(eventMapper.findOwnerByIdForUpdate(7)).thenReturn(lockedEvent());
        when(dataScopeService.currentUser()).thenReturn(user());
        when(attachmentMapper.findActiveByIdForUpdate(11L)).thenReturn(row(storageKey));
        when(attachmentMapper.softDelete(11L, 5L)).thenReturn(1);

        service().delete("7", "11");

        var order = inOrder(attachmentMapper, purgeService);
        order.verify(attachmentMapper).softDelete(11L, 5L);
        order.verify(purgeService).purgeEventAttachment(11L, storageKey);
    }

    @Test
    void deleteCompletesWhenTheOriginalPhysicalFileIsAlreadyMissing() {
        String storageKey = "00000000-0000-0000-0000-000000000020";
        when(eventMapper.findOwnerByIdForUpdate(7)).thenReturn(lockedEvent());
        when(dataScopeService.currentUser()).thenReturn(user());
        when(attachmentMapper.findActiveByIdForUpdate(11L)).thenReturn(row(storageKey));
        when(attachmentMapper.softDelete(11L, 5L)).thenReturn(1);

        assertThatCode(() -> service().delete("7", "11")).doesNotThrowAnyException();

        verify(attachmentMapper).softDelete(11L, 5L);
    }

    @Test
    void rollbackDoesNotTriggerDeferredFilePurge() {
        String storageKey = "00000000-0000-0000-0000-000000000021";
        when(eventMapper.findOwnerByIdForUpdate(7)).thenReturn(lockedEvent());
        when(dataScopeService.currentUser()).thenReturn(user());
        when(attachmentMapper.findActiveByIdForUpdate(11L)).thenReturn(row(storageKey));
        when(attachmentMapper.softDelete(11L, 5L)).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service().delete("7", "11");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(purgeService, never()).purgeEventAttachment(11L, storageKey);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private EventAttachmentService service() {
        return new EventAttachmentService(
                attachmentMapper,
                eventMapper,
                dataScopeService,
                purgeService,
                new AppProperties(
                        new AppProperties.Security(List.of(), ""),
                        new AppProperties.Attachment(
                                storageRoot.toString(),
                                10 * 1024 * 1024,
                                List.of("image/jpeg", "image/png", "application/pdf")
                        ),
                        new AppProperties.Bootstrap(
                                new AppProperties.Admin(false, "admin", "", "系统管理员")
                        )
                )
        );
    }

    private EventMapper.EventRow event() {
        return new EventMapper.EventRow(
                7L, "EVT-7", 1L, "矛盾纠纷", 9L, "第一网格",
                "事件", "描述", "地址", "WEB", "MEDIUM", "REPORTED",
                null, null, null, LocalDateTime.now(), 0
        );
    }

    private EventMapper.EventOwnerRow lockedEvent() {
        return new EventMapper.EventOwnerRow(7L, 9L, 5L, "REPORTED");
    }

    private EventAttachmentMapper.AttachmentRow row(String storageKey) {
        return row(storageKey, null, 5L);
    }

    private EventAttachmentMapper.AttachmentRow row(String storageKey, long uploadedBy) {
        return row(storageKey, null, uploadedBy);
    }

    private EventAttachmentMapper.AttachmentRow row(String storageKey, String uploadToken, long uploadedBy) {
        return new EventAttachmentMapper.AttachmentRow(
                11L, 7L, 9L, storageKey, "现场照片.jpg", "image/jpeg",
                jpegBytes().length, "hash", uploadToken, uploadedBy, "上传人", LocalDateTime.now()
        );
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(
                5L, "staff", "", "上传人", true,
                Set.of("COMMUNITY_STAFF"), Set.of("file:upload", "file:read")
        );
    }

    private AuthenticatedUser residentUser() {
        return new AuthenticatedUser(
                5L, "resident", "", "居民", true,
                Set.of("RESIDENT"), Set.of("resident:portal")
        );
    }

    private byte[] jpegBytes() {
        return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 1, 2, 3};
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    }
}
