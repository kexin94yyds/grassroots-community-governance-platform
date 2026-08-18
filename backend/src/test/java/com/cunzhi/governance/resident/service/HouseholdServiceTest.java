package com.cunzhi.governance.resident.service;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.resident.dto.HouseholdStatusRequest;
import com.cunzhi.governance.resident.mapper.HouseholdMapper;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.service.DataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class HouseholdServiceTest {

    @Mock
    private HouseholdMapper householdMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private DataScopeMapper dataScopeMapper;
    @Mock
    private BusinessNumberGenerator numberGenerator;

    @Test
    void cannotArchiveHouseholdWithActiveResidents() {
        when(householdMapper.findById(11)).thenReturn(new HouseholdMapper.HouseholdRow(
                11L, "HH001", 7L, "第一网格", "1", "2", "301", "测试路1号", "ACTIVE", 4
        ));
        when(householdMapper.countActiveResidents(11)).thenReturn(1);

        assertThatThrownBy(() -> service().updateStatus(
                "11", new HouseholdStatusRequest("ARCHIVED", 4)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(householdMapper, never()).updateStatus(11, "ARCHIVED", 4);
    }

    @Test
    void passesNormalizedKeywordAndStatusToScopedListQueries() {
        when(dataScopeService.currentScope()).thenReturn(DataScope.all());
        when(householdMapper.findPage("HH001", 7L, "ACTIVE", true, List.of(), 0, 20))
                .thenReturn(List.of(new HouseholdMapper.HouseholdRow(
                        11L, "HH001", 7L, "第一网格",
                        "1", "2", "301", "测试路1号", "ACTIVE", 4
                )));
        when(householdMapper.count("HH001", 7L, "ACTIVE", true, List.of()))
                .thenReturn(1L);

        var result = service().findPage(" HH001 ", "7", "ACTIVE", 1, 20);

        assertThat(result.items()).singleElement()
                .extracting(item -> item.gridName())
                .isEqualTo("第一网格");
        verify(dataScopeService).requireGridAccess(7);
        verify(householdMapper).findPage("HH001", 7L, "ACTIVE", true, List.of(), 0, 20);
        verify(householdMapper).count("HH001", 7L, "ACTIVE", true, List.of());
    }

    private HouseholdService service() {
        return new HouseholdService(
                householdMapper, dataScopeService, dataScopeMapper, numberGenerator
        );
    }
}
