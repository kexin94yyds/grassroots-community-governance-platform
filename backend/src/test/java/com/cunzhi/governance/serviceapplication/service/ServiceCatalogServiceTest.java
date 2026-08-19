package com.cunzhi.governance.serviceapplication.service;

import com.cunzhi.governance.serviceapplication.dto.ServiceCatalogUpdateRequest;
import com.cunzhi.governance.serviceapplication.mapper.ServiceCatalogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogServiceTest {

    @Mock
    private ServiceCatalogMapper mapper;

    @Test
    void disablingCatalogLocksRowBeforeCheckingOpenApplicationsAndUpdating() {
        var locked = catalog(8L, "ENABLED", 2);
        var updated = catalog(8L, "DISABLED", 3);
        when(mapper.findByIdForUpdate(8L)).thenReturn(locked);
        when(mapper.countOpenApplications(8L)).thenReturn(0);
        when(mapper.update(8L, "养老关怀", null, 10, "DISABLED", 2)).thenReturn(1);
        when(mapper.findById(8L)).thenReturn(updated);

        var result = service().update("8", new ServiceCatalogUpdateRequest(
                "养老关怀", null, 10, "DISABLED", 2
        ));

        assertThat(result.status()).isEqualTo("DISABLED");
        InOrder order = inOrder(mapper);
        order.verify(mapper).findByIdForUpdate(8L);
        order.verify(mapper).countOpenApplications(8L);
        order.verify(mapper).update(8L, "养老关怀", null, 10, "DISABLED", 2);
    }

    private ServiceCatalogService service() {
        return new ServiceCatalogService(mapper);
    }

    private ServiceCatalogMapper.CatalogRow catalog(long id, String status, int version) {
        return new ServiceCatalogMapper.CatalogRow(id, "ELDERLY_CARE", "养老关怀", null, 10, status, version);
    }
}
