package com.cunzhi.governance.resident.service;

import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.resident.dto.HouseholdCreateRequest;
import com.cunzhi.governance.resident.dto.HouseholdStatusRequest;
import com.cunzhi.governance.resident.dto.HouseholdSummary;
import com.cunzhi.governance.resident.dto.HouseholdUpdateRequest;
import com.cunzhi.governance.resident.mapper.HouseholdMapper;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class HouseholdService {

    private final HouseholdMapper householdMapper;
    private final DataScopeService dataScopeService;
    private final DataScopeMapper dataScopeMapper;
    private final BusinessNumberGenerator numberGenerator;

    public HouseholdService(
            HouseholdMapper householdMapper,
            DataScopeService dataScopeService,
            DataScopeMapper dataScopeMapper,
            BusinessNumberGenerator numberGenerator
    ) {
        this.householdMapper = householdMapper;
        this.dataScopeService = dataScopeService;
        this.dataScopeMapper = dataScopeMapper;
        this.numberGenerator = numberGenerator;
    }

    public PageResponse<HouseholdSummary> findPage(
            String keyword,
            String gridId,
            String status,
            int page,
            int size
    ) {
        Long requestedGridId = gridId == null ? null : IdParser.parse(gridId, "网格ID");
        if (requestedGridId != null) {
            dataScopeService.requireGridAccess(requestedGridId);
        }
        DataScope scope = dataScopeService.currentScope();
        boolean allAccess = scope.type() == DataScopeType.ALL;
        List<Long> gridIds = new ArrayList<>(scope.gridIds());
        String normalizedKeyword = normalizeText(keyword);
        String normalizedStatus = normalizeStatus(status);
        List<HouseholdSummary> items = householdMapper.findPage(
                        normalizedKeyword,
                        requestedGridId,
                        normalizedStatus,
                        allAccess,
                        gridIds,
                        (page - 1) * size,
                        size
                ).stream()
                .map(this::toSummary)
                .toList();
        return new PageResponse<>(
                items,
                householdMapper.count(
                        normalizedKeyword, requestedGridId, normalizedStatus, allAccess, gridIds
                ),
                page,
                size
        );
    }

    public HouseholdSummary findById(String id) {
        HouseholdMapper.HouseholdRow row = requireHousehold(IdParser.parse(id, "家庭户ID"));
        dataScopeService.requireGridAccess(row.gridId());
        return toSummary(row);
    }

    @Transactional
    public HouseholdSummary create(HouseholdCreateRequest request) {
        long gridId = IdParser.parse(request.gridId(), "网格ID");
        dataScopeService.requireGridAccess(gridId);
        requireEnabledGrid(gridId);
        String householdNo = numberGenerator.next("HH");
        householdMapper.insert(
                householdNo, gridId, normalizeText(request.buildingNo()),
                normalizeText(request.unitNo()), normalizeText(request.roomNo()),
                request.address().trim()
        );
        Long id = householdMapper.findIdByHouseholdNo(householdNo);
        if (id == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建家庭户后未能读取家庭户");
        }
        return toSummary(requireHousehold(id));
    }

    @Transactional
    public HouseholdSummary update(String id, HouseholdUpdateRequest request) {
        long householdId = IdParser.parse(id, "家庭户ID");
        HouseholdMapper.HouseholdRow row = requireHousehold(householdId);
        dataScopeService.requireGridAccess(row.gridId());
        ensureUpdated(householdMapper.update(
                householdId, normalizeText(request.buildingNo()), normalizeText(request.unitNo()),
                normalizeText(request.roomNo()), request.address().trim(), request.version()
        ));
        return toSummary(requireHousehold(householdId));
    }

    @Transactional
    public HouseholdSummary updateStatus(String id, HouseholdStatusRequest request) {
        long householdId = IdParser.parse(id, "家庭户ID");
        HouseholdMapper.HouseholdRow row = requireHousehold(householdId);
        dataScopeService.requireGridAccess(row.gridId());
        if (!"ACTIVE".equals(request.status()) && householdMapper.countActiveResidents(householdId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "家庭户仍有有效居民，不能迁出或归档");
        }
        if ("ACTIVE".equals(request.status())) {
            requireEnabledGrid(row.gridId());
        }
        ensureUpdated(householdMapper.updateStatus(householdId, request.status(), request.version()));
        return toSummary(requireHousehold(householdId));
    }

    private HouseholdMapper.HouseholdRow requireHousehold(long id) {
        HouseholdMapper.HouseholdRow row = householdMapper.findById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "家庭户不存在");
        }
        return row;
    }

    private void requireEnabledGrid(long gridId) {
        if (dataScopeMapper.countEnabledGrid(gridId) != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "网格不存在或已停用");
        }
    }

    private HouseholdSummary toSummary(HouseholdMapper.HouseholdRow row) {
        return new HouseholdSummary(
                row.id().toString(), row.householdNo(), row.gridId().toString(),
                row.gridName(),
                row.buildingNo(), row.unitNo(), row.roomNo(), row.address(), row.status(), row.version()
        );
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeText(status);
        if (normalized == null) {
            return null;
        }
        if (!List.of("ACTIVE", "MOVED", "ARCHIVED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知家庭户状态");
        }
        return normalized;
    }

    private void ensureUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }
}
