package com.cunzhi.governance.serviceapplication.service;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.serviceapplication.dto.ServiceCatalogCreateRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceCatalogUpdateRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceCatalogView;
import com.cunzhi.governance.serviceapplication.mapper.ServiceCatalogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServiceCatalogService {

    private final ServiceCatalogMapper mapper;

    public ServiceCatalogService(ServiceCatalogMapper mapper) {
        this.mapper = mapper;
    }

    public List<ServiceCatalogView> findEnabled() {
        return mapper.findAll(false).stream().map(this::toView).toList();
    }

    public List<ServiceCatalogView> findAll() {
        return mapper.findAll(true).stream().map(this::toView).toList();
    }

    @Transactional
    public ServiceCatalogView create(ServiceCatalogCreateRequest request) {
        String code = request.code().trim();
        String status = request.status() == null ? "ENABLED" : request.status();
        ensureUpdated(mapper.insert(
                code, request.name().trim(), normalize(request.description()),
                request.sortNo() == null ? 0 : request.sortNo(), status
        ));
        Long id = mapper.findIdByCode(code);
        if (id == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建服务目录后未能读取记录");
        }
        return toView(require(id));
    }

    @Transactional
    public ServiceCatalogView update(String idValue, ServiceCatalogUpdateRequest request) {
        long id = IdParser.parse(idValue, "服务目录ID");
        ServiceCatalogMapper.CatalogRow current = requireLocked(id);
        if ("ENABLED".equals(current.status()) && "DISABLED".equals(request.status())
                && mapper.countOpenApplications(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "仍有未终结服务申请，不能停用目录");
        }
        ensureUpdated(mapper.update(
                id, request.name().trim(), normalize(request.description()), request.sortNo(),
                request.status(), request.version()
        ));
        return toView(require(id));
    }

    private ServiceCatalogMapper.CatalogRow require(long id) {
        ServiceCatalogMapper.CatalogRow row = mapper.findById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务目录不存在");
        }
        return row;
    }

    private ServiceCatalogMapper.CatalogRow requireLocked(long id) {
        ServiceCatalogMapper.CatalogRow row = mapper.findByIdForUpdate(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务目录不存在");
        }
        return row;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private ServiceCatalogView toView(ServiceCatalogMapper.CatalogRow row) {
        return new ServiceCatalogView(
                row.id().toString(), row.code(), row.name(), row.description(),
                row.sortNo(), row.status(), row.version()
        );
    }
}
