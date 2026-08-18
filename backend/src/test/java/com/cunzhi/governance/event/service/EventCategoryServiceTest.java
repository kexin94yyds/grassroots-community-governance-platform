package com.cunzhi.governance.event.service;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.event.dto.EventCategoryCreateRequest;
import com.cunzhi.governance.event.dto.EventCategoryUpdateRequest;
import com.cunzhi.governance.event.mapper.EventCategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCategoryServiceTest {

    @Mock
    private EventCategoryMapper mapper;

    @Test
    void createsCategoryEnabledByDefaultWhenStatusIsOmitted() {
        when(mapper.countByCode("PUBLIC_ORDER")).thenReturn(0);
        when(mapper.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.findIdByCode("PUBLIC_ORDER")).thenReturn(8L);
        when(mapper.findById(8L)).thenReturn(category(8L, "PUBLIC_ORDER", "公共秩序", "ENABLED", 0));

        var result = service().create(new EventCategoryCreateRequest(
                "PUBLIC_ORDER", "公共秩序", "  占道经营和秩序维护  ", 15, null
        ));

        ArgumentCaptor<EventCategoryMapper.CategoryRow> captor = ArgumentCaptor.forClass(EventCategoryMapper.CategoryRow.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue()).satisfies(row -> {
            assertThat(row.code()).isEqualTo("PUBLIC_ORDER");
            assertThat(row.description()).isEqualTo("占道经营和秩序维护");
            assertThat(row.status()).isEqualTo("ENABLED");
            assertThat(row.version()).isZero();
        });
        assertThat(result).extracting(item -> item.status()).isEqualTo("ENABLED");
    }

    @Test
    void refusesToDisableCategoryStillUsedByNonTerminalEvent() {
        when(mapper.findByIdForUpdate(8L)).thenReturn(category(8L, "PUBLIC_ORDER", "公共秩序", "ENABLED", 3));
        when(mapper.countNonTerminalEvents(8L)).thenReturn(1);

        assertThatThrownBy(() -> service().update("8", update("DISABLED", 3)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(mapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void convertsConcurrentDuplicateCategoryCodeToConflict() {
        when(mapper.countByCode("PUBLIC_ORDER")).thenReturn(0);
        when(mapper.insert(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DuplicateKeyException("uk_event_category_code"));

        assertThatThrownBy(() -> service().create(new EventCategoryCreateRequest(
                "PUBLIC_ORDER", "公共秩序", null, null, null
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void allowsDisableWhenOnlyTerminalHistoryReferencesCategory() {
        when(mapper.findByIdForUpdate(8L)).thenReturn(category(8L, "PUBLIC_ORDER", "公共秩序", "ENABLED", 3));
        when(mapper.findById(8L)).thenReturn(category(8L, "PUBLIC_ORDER", "公共秩序", "DISABLED", 4));
        when(mapper.countNonTerminalEvents(8L)).thenReturn(0);
        when(mapper.update(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        var result = service().update("8", update("DISABLED", 3));

        assertThat(result.status()).isEqualTo("DISABLED");
        assertThat(result.version()).isEqualTo(4);
    }

    @Test
    void rejectsStaleVersionInsteadOfSilentlyOverwritingCategory() {
        when(mapper.findByIdForUpdate(8L)).thenReturn(category(8L, "PUBLIC_ORDER", "公共秩序", "ENABLED", 3));
        when(mapper.update(org.mockito.ArgumentMatchers.any())).thenReturn(0);

        assertThatThrownBy(() -> service().update("8", update("ENABLED", 2)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    private EventCategoryService service() {
        return new EventCategoryService(mapper);
    }

    private EventCategoryUpdateRequest update(String status, int version) {
        return new EventCategoryUpdateRequest("公共秩序", "说明", 15, status, version);
    }

    private EventCategoryMapper.CategoryRow category(
            long id, String code, String name, String status, int version
    ) {
        return new EventCategoryMapper.CategoryRow(id, code, name, "说明", 15, status, version);
    }
}
