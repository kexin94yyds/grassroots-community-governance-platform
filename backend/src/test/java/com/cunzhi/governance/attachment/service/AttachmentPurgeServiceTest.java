package com.cunzhi.governance.attachment.service;

import com.cunzhi.governance.attachment.mapper.EventAttachmentMapper;
import com.cunzhi.governance.config.AppProperties;
import com.cunzhi.governance.task.mapper.TaskAttachmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentPurgeServiceTest {

    @TempDir
    Path storageRoot;

    @Mock
    private EventAttachmentMapper eventAttachmentMapper;
    @Mock
    private TaskAttachmentMapper taskAttachmentMapper;
    @Mock
    private AttachmentPurgeMarkerService markerService;

    @Test
    void deletesExistingEventFileThenMarksItPurged() throws Exception {
        String storageKey = "00000000-0000-0000-0000-000000000001";
        Files.write(storageRoot.resolve(storageKey), new byte[]{1});

        service().purgeEventAttachment(7L, storageKey);

        assertThat(Files.exists(storageRoot.resolve(storageKey))).isFalse();
        verify(markerService).markEventFilePurged(7L);
    }

    @Test
    void marksTaskAttachmentPurgedWhenItsPhysicalFileIsAlreadyMissing() {
        String storageKey = "00000000-0000-0000-0000-000000000002";

        service().purgeTaskAttachment(8L, storageKey);

        verify(markerService).markTaskFilePurged(8L);
    }

    @Test
    void retainsPendingMarkerWhenPhysicalDeletionFails() throws Exception {
        String storageKey = "00000000-0000-0000-0000-000000000003";
        Path nonEmptyDirectory = Files.createDirectory(storageRoot.resolve(storageKey));
        Files.write(nonEmptyDirectory.resolve("child"), new byte[]{1});

        service().purgeEventAttachment(9L, storageKey);

        verify(markerService, never()).markEventFilePurged(9L);
        assertThat(Files.exists(nonEmptyDirectory)).isTrue();
    }

    @Test
    void retriesBothPendingQueuesWithinTheConfiguredBatchLimit() {
        String eventKey = "00000000-0000-0000-0000-000000000004";
        String taskKey = "00000000-0000-0000-0000-000000000005";
        when(eventAttachmentMapper.findPendingFilePurges(AttachmentPurgeService.DEFAULT_BATCH_LIMIT))
                .thenReturn(List.of(new EventAttachmentMapper.PendingFilePurgeRow(10L, eventKey)));
        when(taskAttachmentMapper.findPendingFilePurges(AttachmentPurgeService.DEFAULT_BATCH_LIMIT))
                .thenReturn(List.of(new TaskAttachmentMapper.PendingFilePurgeRow(11L, taskKey)));

        service().retryPendingAttachments(10_000);

        verify(markerService).markEventFilePurged(10L);
        verify(markerService).markTaskFilePurged(11L);
    }

    private AttachmentPurgeService service() {
        return new AttachmentPurgeService(
                eventAttachmentMapper,
                taskAttachmentMapper,
                markerService,
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
}
