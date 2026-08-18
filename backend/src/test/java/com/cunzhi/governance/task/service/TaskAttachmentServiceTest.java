package com.cunzhi.governance.task.service;

import com.cunzhi.governance.attachment.service.AttachmentPurgeService;
import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.config.AppProperties;
import com.cunzhi.governance.system.service.DataScopeService;
import com.cunzhi.governance.task.mapper.TaskAttachmentMapper;
import com.cunzhi.governance.task.mapper.TaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAttachmentServiceTest {

    @TempDir
    Path storageRoot;

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TaskAttachmentMapper attachmentMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private AttachmentPurgeService purgeService;

    @Test
    void refusesUploadByUserWhoIsNotTheTaskAssignee() {
        when(taskMapper.findByIdForUpdate(42L)).thenReturn(Optional.of(task(42L, "PROCESSING", 12L)));
        when(dataScopeService.currentUser()).thenReturn(user(13L));

        assertThatThrownBy(() -> service().upload(
                "42", new MockMultipartFile("file", "现场.jpg", "image/jpeg", jpegBytes())
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(dataScopeService).requireGridAccess(7L);
        verify(attachmentMapper, never()).countActiveByTaskId(42L);
    }

    @Test
    void refusesDeleteAfterTaskReachedTerminalState() {
        when(taskMapper.findByIdForUpdate(42L)).thenReturn(Optional.of(task(42L, "COMPLETED", 12L)));

        assertThatThrownBy(() -> service().delete("42", "9"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(attachmentMapper, never()).findActiveByIdForUpdate(9L);
    }

    @Test
    void refusesToDownloadAttachmentAttachedToAnotherTask() {
        when(taskMapper.findById(42L)).thenReturn(Optional.of(task(42L, "PROCESSING", 12L)));
        when(attachmentMapper.findById(9L)).thenReturn(new TaskAttachmentMapper.AttachmentRow(
                9L, 43L, 7L, "00000000-0000-0000-0000-000000000009", "别的任务.jpg",
                "image/jpeg", jpegBytes().length, "hash", null, 12L, "执行人", LocalDateTime.now()
        ));

        assertThatThrownBy(() -> service().download("42", "9"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(dataScopeService).requireGridAccess(7L);
    }

    @Test
    void rejectsTwentyFirstActiveTaskAttachmentBeforeStoringFile() throws Exception {
        when(taskMapper.findByIdForUpdate(42L)).thenReturn(Optional.of(task(42L, "PROCESSING", 12L)));
        when(dataScopeService.currentUser()).thenReturn(user(12L));
        when(attachmentMapper.countActiveByTaskId(42L)).thenReturn(20);

        assertThatThrownBy(() -> service().upload(
                "42", new MockMultipartFile("file", "第21个.jpg", "image/jpeg", jpegBytes())
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        assertThat(Files.list(storageRoot).findAny()).isEmpty();
        verify(attachmentMapper, never()).insert(
                eq(42L), anyString(), anyString(), anyString(), eq((long) jpegBytes().length), anyString(), isNull(), eq(12L)
        );
    }

    @Test
    void returnsExistingAttachmentForRepeatedActiveRequestTokenBeforeMutableTaskChecks() throws Exception {
        String token = "123e4567-e89b-12d3-a456-426614174000";
        when(taskMapper.findByIdForUpdate(42L)).thenReturn(Optional.of(task(42L, "COMPLETED", 99L)));
        when(dataScopeService.currentUser()).thenReturn(user(12L));
        when(attachmentMapper.findActiveByTaskAndUploaderAndUploadToken(42L, 12L, token))
                .thenReturn(new TaskAttachmentMapper.AttachmentRow(
                        9L, 42L, 7L, "00000000-0000-0000-0000-000000000009", "重试.jpg",
                        "image/jpeg", jpegBytes().length, "hash", token, 12L, "执行人", LocalDateTime.now()
                ));

        var result = service().upload(
                "42", new MockMultipartFile("file", "重试.jpg", "image/jpeg", jpegBytes()), token
        );

        assertThat(result.id()).isEqualTo("9");
        assertThat(Files.list(storageRoot).findAny()).isEmpty();
        verify(attachmentMapper, never()).countActiveByTaskId(42L);
        verify(attachmentMapper, never()).insert(
                eq(42L), anyString(), anyString(), anyString(), eq((long) jpegBytes().length), anyString(), eq(token), eq(12L)
        );
    }

    @Test
    void directDeleteSoftDeletesThenRemovesPhysicalFileWithoutStaging() throws Exception {
        String storageKey = "00000000-0000-0000-0000-000000000019";
        when(taskMapper.findByIdForUpdate(42L)).thenReturn(Optional.of(task(42L, "PROCESSING", 12L)));
        when(dataScopeService.currentUser()).thenReturn(user(12L));
        when(attachmentMapper.findActiveByIdForUpdate(9L)).thenReturn(new TaskAttachmentMapper.AttachmentRow(
                9L, 42L, 7L, storageKey, "现场.jpg", "image/jpeg", jpegBytes().length,
                "hash", null, 12L, "执行人", LocalDateTime.now()
        ));
        when(attachmentMapper.softDelete(9L, 12L)).thenReturn(1);

        service().delete("42", "9");

        var order = inOrder(attachmentMapper, purgeService);
        order.verify(attachmentMapper).softDelete(9L, 12L);
        order.verify(purgeService).purgeTaskAttachment(9L, storageKey);
    }

    private TaskAttachmentService service() {
        return new TaskAttachmentService(
                taskMapper,
                attachmentMapper,
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

    private TaskMapper.TaskRow task(long id, String status, long assigneeId) {
        return new TaskMapper.TaskRow(
                id, "TSK-" + id, null, null, 7L, "第一网格", "OTHER", "巡查任务", null,
                "MEDIUM", status, 5L, "派发人", assigneeId, "执行人", null,
                LocalDateTime.now(), null, null, 0
        );
    }

    private AuthenticatedUser user(long id) {
        return new AuthenticatedUser(
                id, "worker", "", "执行人", true,
                Set.of("GRID_WORKER"), Set.of("task:read", "task:handle", "file:delete")
        );
    }

    private byte[] jpegBytes() {
        return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 1, 2, 3};
    }
}
