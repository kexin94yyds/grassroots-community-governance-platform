package com.cunzhi.governance.event.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.event.dto.EventCreateRequest;
import com.cunzhi.governance.event.mapper.EventFlowMapper;
import com.cunzhi.governance.event.mapper.EventMapper;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.service.DataScopeService;
import com.cunzhi.governance.task.mapper.TaskFlowMapper;
import com.cunzhi.governance.task.mapper.TaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventMapper eventMapper;
    @Mock private EventFlowMapper eventFlowMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private TaskFlowMapper taskFlowMapper;
    @Mock private DataScopeMapper dataScopeMapper;
    @Mock private DataScopeService dataScopeService;
    @Mock private BusinessNumberGenerator numberGenerator;

    @Test
    void reportLocksEnabledCategoryBeforeInsertingEvent() {
        when(dataScopeMapper.countEnabledGrid(7L)).thenReturn(1);
        when(eventMapper.lockEnabledCategory(2L)).thenReturn(2L);
        when(dataScopeService.currentUser()).thenReturn(user());
        when(numberGenerator.next("EVT")).thenReturn("EVT0001");
        when(eventMapper.findIdByEventNo("EVT0001")).thenReturn(12L);
        when(eventMapper.findById(12L)).thenReturn(Optional.of(event()));

        var result = service().report(new EventCreateRequest(
                "2", "7", "楼道堆物", "请处理", "WEB", "MEDIUM", "1号楼", "居民甲"
        ));

        assertThat(result.id()).isEqualTo("12");
        verify(eventMapper).lockEnabledCategory(2L);
    }

    private EventService service() {
        return new EventService(eventMapper, eventFlowMapper, taskMapper, taskFlowMapper,
                dataScopeMapper, dataScopeService, numberGenerator);
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(5L, "staff", "", "工作人员", true,
                Set.of("COMMUNITY_STAFF"), Set.of("event:report"));
    }

    private EventMapper.EventRow event() {
        return new EventMapper.EventRow(12L, "EVT0001", 2L, "环境卫生", 7L, "第一网格",
                "楼道堆物", "请处理", "1号楼", "WEB", "MEDIUM", "REPORTED", null,
                null, null, LocalDateTime.now(), 0);
    }
}
