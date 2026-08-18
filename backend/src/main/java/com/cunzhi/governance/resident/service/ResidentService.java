package com.cunzhi.governance.resident.service;

import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.resident.dto.ResidentCreateRequest;
import com.cunzhi.governance.resident.dto.ResidentSensitiveSearchRequest;
import com.cunzhi.governance.resident.dto.ResidentSensitiveView;
import com.cunzhi.governance.resident.dto.ResidentSensitiveAccessLogView;
import com.cunzhi.governance.resident.dto.ResidentSensitiveViewRequest;
import com.cunzhi.governance.resident.dto.ResidentStatusRequest;
import com.cunzhi.governance.resident.dto.ResidentSummary;
import com.cunzhi.governance.resident.dto.ResidentUpdateRequest;
import com.cunzhi.governance.resident.mapper.HouseholdMapper;
import com.cunzhi.governance.resident.mapper.ResidentMapper;
import com.cunzhi.governance.resident.mapper.ResidentSensitiveAccessMapper;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.mapper.SystemUserMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.security.PermissionCodes;
import com.cunzhi.governance.system.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class ResidentService {

    private final ResidentMapper residentMapper;
    private final ResidentSensitiveAccessMapper sensitiveAccessMapper;
    private final HouseholdMapper householdMapper;
    private final DataScopeService dataScopeService;
    private final DataScopeMapper dataScopeMapper;
    private final SystemUserMapper systemUserMapper;
    private final BusinessNumberGenerator numberGenerator;
    private final SensitiveDataCodec sensitiveDataCodec;
    private final ObjectMapper objectMapper;

    public ResidentService(
            ResidentMapper residentMapper,
            ResidentSensitiveAccessMapper sensitiveAccessMapper,
            HouseholdMapper householdMapper,
            DataScopeService dataScopeService,
            DataScopeMapper dataScopeMapper,
            SystemUserMapper systemUserMapper,
            BusinessNumberGenerator numberGenerator,
            SensitiveDataCodec sensitiveDataCodec,
            ObjectMapper objectMapper
    ) {
        this.residentMapper = residentMapper;
        this.sensitiveAccessMapper = sensitiveAccessMapper;
        this.householdMapper = householdMapper;
        this.dataScopeService = dataScopeService;
        this.dataScopeMapper = dataScopeMapper;
        this.systemUserMapper = systemUserMapper;
        this.numberGenerator = numberGenerator;
        this.sensitiveDataCodec = sensitiveDataCodec;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResidentSummary create(ResidentCreateRequest request) {
        long gridId = IdParser.parse(request.gridId(), "网格ID");
        dataScopeService.requireGridAccess(gridId);
        requireEnabledGrid(gridId);
        Long householdId = parseOptionalId(request.householdId(), "家庭户ID");
        validateHousehold(householdId, gridId, request.isHouseholder(), 0);

        SensitiveDataCodec.Encoded idCard = encodeIdCard(request.idCard());
        SensitiveDataCodec.Encoded phone = encodePhone(request.phone());
        requireUniqueIdCard(idCard, 0);
        String residentNo = numberGenerator.next("RES");
        residentMapper.insert(
                residentNo, gridId, householdId, request.realName().trim(), request.gender(),
                request.birthDate(), ciphertext(idCard), hash(idCard), last4(idCard),
                ciphertext(phone), hash(phone), last4(phone), request.address().trim(),
                request.isHouseholder(), tagsToJson(request.specialGroupTags()),
                normalizeText(request.remark()), dataScopeService.currentUser().id()
        );
        Long residentId = residentMapper.findIdByResidentNo(residentNo);
        if (residentId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建居民后未能读取居民");
        }
        return toSummary(requireResident(residentId));
    }

    @Transactional
    public ResidentSummary update(String id, ResidentUpdateRequest request) {
        long residentId = IdParser.parse(id, "居民ID");
        ResidentMapper.ResidentRow row = requireResident(residentId);
        dataScopeService.requireGridAccess(row.gridId());
        Long householdId = parseOptionalId(request.householdId(), "家庭户ID");
        validateHousehold(householdId, row.gridId(), request.isHouseholder(), residentId);

        SensitiveDataCodec.Encoded idCard = encodeIdCard(request.idCard());
        SensitiveDataCodec.Encoded phone = encodePhone(request.phone());
        requireUniqueIdCard(idCard, residentId);
        ensureUpdated(residentMapper.update(
                residentId, householdId, request.realName().trim(), request.gender(),
                request.birthDate(), ciphertext(idCard), hash(idCard), last4(idCard),
                ciphertext(phone), hash(phone), last4(phone), request.address().trim(),
                request.isHouseholder(), tagsToJson(request.specialGroupTags()),
                normalizeText(request.remark()), request.version()
        ));
        return toSummary(requireResident(residentId));
    }

    @Transactional
    public ResidentSummary updateStatus(String id, ResidentStatusRequest request) {
        long residentId = IdParser.parse(id, "居民ID");
        ResidentMapper.ResidentRow row = requireResident(residentId);
        dataScopeService.requireGridAccess(row.gridId());
        if (row.status().equals(request.status())) {
            if (row.version() != request.version()) {
                throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
            }
            return toSummary(row);
        }
        if ("ACTIVE".equals(request.status())) {
            requireEnabledGrid(row.gridId());
            validateHousehold(row.householdId(), row.gridId(), row.householder(), residentId);
        } else {
            Long linkedUserId = residentMapper.findLinkedUserId(residentId);
            if (linkedUserId != null) {
                ensureUpdated(systemUserMapper.disableLinkedResidentUser(linkedUserId));
            }
        }
        ensureUpdated(residentMapper.updateStatus(residentId, request.status(), request.version()));
        return toSummary(requireResident(residentId));
    }

    public ResidentSummary findById(String id) {
        ResidentMapper.ResidentRow row = residentMapper.findById(IdParser.parse(id, "居民ID"))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "居民不存在"));
        dataScopeService.requireGridAccess(row.gridId());
        return toSummary(row);
    }

    public ResidentSummary findByCurrentUser() {
        long userId = dataScopeService.currentUser().id();
        ResidentMapper.ResidentRow row = residentMapper.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "当前账号未绑定居民档案"));
        return toSummary(row);
    }

    public PageResponse<ResidentSummary> findPage(
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
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        String normalizedStatus = normalizeStatus(status);
        List<ResidentSummary> items = residentMapper.findPage(
                        normalizedKeyword,
                        null,
                        null,
                        requestedGridId,
                        normalizedStatus,
                        allAccess,
                        gridIds,
                        (page - 1) * size,
                        size
                ).stream()
                .map(this::toSummary)
                .toList();
        long total = residentMapper.count(
                normalizedKeyword, null, null, requestedGridId, normalizedStatus, allAccess, gridIds
        );
        return new PageResponse<>(items, total, page, size);
    }

    @Transactional
    public PageResponse<ResidentSummary> findBySensitiveValue(ResidentSensitiveSearchRequest request) {
        requireSensitivePermission();
        Long requestedGridId = request.gridId() == null || request.gridId().isBlank()
                ? null
                : IdParser.parse(request.gridId(), "网格ID");
        if (requestedGridId != null) {
            dataScopeService.requireGridAccess(requestedGridId);
        }
        String type = request.type().trim();
        String normalizedValue = "ID_CARD".equals(type)
                ? ResidentIdentityNormalizer.normalizeIdCard(request.value())
                : ResidentIdentityNormalizer.normalizePhone(request.value());
        String fingerprint = sensitiveDataCodec.fingerprint(normalizedValue);
        String idCardHash = "ID_CARD".equals(type) ? fingerprint : null;
        String phoneHash = "PHONE".equals(type) ? fingerprint : null;
        String normalizedStatus = normalizeStatus(request.status());
        DataScope scope = dataScopeService.currentScope();
        boolean allAccess = scope.type() == DataScopeType.ALL;
        List<Long> gridIds = new ArrayList<>(scope.gridIds());
        List<ResidentSummary> items = residentMapper.findPage(
                        null,
                        idCardHash,
                        phoneHash,
                        requestedGridId,
                        normalizedStatus,
                        allAccess,
                        gridIds,
                        (request.page() - 1) * request.size(),
                        request.size()
                ).stream()
                .map(this::toSummary)
                .toList();
        long total = residentMapper.count(
                null, idCardHash, phoneHash, requestedGridId, normalizedStatus, allAccess, gridIds
        );
        ensureAuditInserted(sensitiveAccessMapper.insert(
                dataScopeService.currentUser().id(),
                null,
                auditScopeGridId(requestedGridId, scope),
                "SEARCH",
                type,
                null,
                (int) Math.min(total, Integer.MAX_VALUE)
        ));
        return new PageResponse<>(items, total, request.page(), request.size());
    }

    @Transactional
    public ResidentSensitiveView viewSensitive(String id, ResidentSensitiveViewRequest request) {
        requireSensitivePermission();
        long residentId = IdParser.parse(id, "居民ID");
        ResidentMapper.ResidentRow resident = requireResident(residentId);
        dataScopeService.requireGridAccess(resident.gridId());
        ResidentMapper.ResidentSensitiveRow sensitive = residentMapper.findSensitiveById(residentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "居民不存在"));
        ResidentSensitiveView result = new ResidentSensitiveView(
                Long.toString(residentId),
                sensitiveDataCodec.decrypt(sensitive.idCardCiphertext()),
                sensitiveDataCodec.decrypt(sensitive.phoneCiphertext())
        );
        ensureAuditInserted(sensitiveAccessMapper.insert(
                dataScopeService.currentUser().id(),
                residentId,
                resident.gridId(),
                "VIEW",
                "BOTH",
                request.purpose().trim(),
                1
        ));
        return result;
    }

    public PageResponse<ResidentSensitiveAccessLogView> findSensitiveAccessLogs(
            String action,
            String fieldType,
            String keyword,
            int page,
            int size
    ) {
        requireSensitiveAuditPermission();
        String normalizedAction = normalizeAuditAction(action);
        String normalizedFieldType = normalizeAuditFieldType(fieldType);
        String normalizedKeyword = normalizeText(keyword);
        DataScope scope = dataScopeService.currentScope();
        boolean allAccess = scope.type() == DataScopeType.ALL;
        List<Long> gridIds = new ArrayList<>(scope.gridIds());
        long userId = dataScopeService.currentUser().id();
        List<ResidentSensitiveAccessLogView> items = sensitiveAccessMapper.findPage(
                        normalizedAction, normalizedFieldType, normalizedKeyword, allAccess, gridIds, userId,
                        (page - 1) * size, size
                ).stream()
                .map(this::toSensitiveAccessLogView)
                .toList();
        long total = sensitiveAccessMapper.countPage(
                normalizedAction, normalizedFieldType, normalizedKeyword, allAccess, gridIds, userId
        );
        return new PageResponse<>(items, total, page, size);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim();
        if (!List.of("ACTIVE", "MOVED", "DECEASED", "ARCHIVED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知居民状态");
        }
        return normalized;
    }

    private String normalizeAuditAction(String action) {
        String normalized = normalizeText(action);
        if (normalized == null) {
            return null;
        }
        String value = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("SEARCH", "VIEW").contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知敏感访问动作");
        }
        return value;
    }

    private String normalizeAuditFieldType(String fieldType) {
        String normalized = normalizeText(fieldType);
        if (normalized == null) {
            return null;
        }
        String value = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("ID_CARD", "PHONE", "BOTH").contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知敏感字段类型");
        }
        return value;
    }

    private ResidentSummary toSummary(ResidentMapper.ResidentRow row) {
        return new ResidentSummary(
                row.id().toString(),
                row.residentNo(),
                row.gridId().toString(),
                row.gridName(),
                row.householdId() == null ? null : row.householdId().toString(),
                row.householdNo(),
                row.realName(),
                row.gender(),
                row.birthDate(),
                mask("**************", row.idCardLast4()),
                mask("*******", row.phoneLast4()),
                row.address(),
                row.householder(),
                tagsFromJson(row.specialGroupTags()),
                row.remark(),
                row.status(),
                row.version()
        );
    }

    private ResidentMapper.ResidentRow requireResident(long id) {
        return residentMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "居民不存在"));
    }

    private void validateHousehold(Long householdId, long gridId, boolean householder, long excludedResidentId) {
        if (householdId == null) {
            if (householder) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "户主必须归属家庭户");
            }
            return;
        }
        HouseholdMapper.HouseholdRow household = householdMapper.findById(householdId);
        if (household == null || !"ACTIVE".equals(household.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "家庭户不存在或已停用");
        }
        if (!household.gridId().equals(gridId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "居民与家庭户必须属于同一网格");
        }
        if (householder
                && residentMapper.countOtherActiveHouseholders(householdId, excludedResidentId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该家庭户已存在有效户主");
        }
    }

    private SensitiveDataCodec.Encoded encodeIdCard(String value) {
        String normalized = normalizeText(value);
        return normalized == null
                ? null
                : sensitiveDataCodec.encode(ResidentIdentityNormalizer.normalizeIdCard(normalized));
    }

    private SensitiveDataCodec.Encoded encodePhone(String value) {
        String normalized = normalizeText(value);
        return normalized == null
                ? null
                : sensitiveDataCodec.encode(ResidentIdentityNormalizer.normalizePhone(normalized));
    }

    private void requireUniqueIdCard(SensitiveDataCodec.Encoded encoded, long excludedId) {
        if (encoded != null && residentMapper.countByIdCardHash(encoded.hash(), excludedId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "身份证号已存在");
        }
    }

    private String tagsToJson(List<String> tags) {
        List<String> normalized = new ArrayList<>(new LinkedHashSet<>(
                tags.stream().map(String::trim).toList()
        ));
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "重点人群标签序列化失败");
        }
    }

    private List<String> tagsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "重点人群标签格式无效");
        }
    }

    private Long parseOptionalId(String value, String fieldName) {
        return value == null || value.isBlank() ? null : IdParser.parse(value, fieldName);
    }

    private void requireEnabledGrid(long gridId) {
        if (dataScopeMapper.countEnabledGrid(gridId) != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "网格不存在或已停用");
        }
    }

    private byte[] ciphertext(SensitiveDataCodec.Encoded encoded) {
        return encoded == null ? null : encoded.ciphertext();
    }

    private String hash(SensitiveDataCodec.Encoded encoded) {
        return encoded == null ? null : encoded.hash();
    }

    private String last4(SensitiveDataCodec.Encoded encoded) {
        return encoded == null ? null : encoded.last4();
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private void ensureAuditInserted(int inserted) {
        if (inserted != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "敏感数据访问审计写入失败");
        }
    }

    private void requireSensitivePermission() {
        if (!dataScopeService.currentUser().permissions().contains(PermissionCodes.RESIDENT_SENSITIVE_READ)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看或检索居民敏感信息");
        }
    }

    private void requireSensitiveAuditPermission() {
        if (!dataScopeService.currentUser().permissions().contains(PermissionCodes.RESIDENT_SENSITIVE_AUDIT_READ)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看敏感数据访问审计");
        }
    }

    private Long auditScopeGridId(Long requestedGridId, DataScope scope) {
        if (requestedGridId != null) {
            return requestedGridId;
        }
        if (scope.type() != DataScopeType.ALL && scope.gridIds().size() == 1) {
            return scope.gridIds().iterator().next();
        }
        return null;
    }

    private ResidentSensitiveAccessLogView toSensitiveAccessLogView(ResidentSensitiveAccessMapper.AccessLogRow row) {
        return new ResidentSensitiveAccessLogView(
                row.id().toString(), row.operatorUserId().toString(), row.operatorName(), row.operatorUsername(),
                row.residentId() == null ? null : row.residentId().toString(),
                maskResidentNo(row.residentNo()), maskResidentName(row.residentName()),
                row.scopeGridId() == null ? null : row.scopeGridId().toString(),
                row.scopeGridCode(), row.scopeGridName(),
                row.action(), row.fieldType(), row.purpose(), row.resultCount(), row.createdAt()
        );
    }

    private String maskResidentName(String name) {
        return name == null || name.isBlank() ? null : name.substring(0, 1) + "*";
    }

    private String maskResidentNo(String residentNo) {
        if (residentNo == null || residentNo.isBlank()) {
            return null;
        }
        return residentNo.length() <= 4 ? "****" : residentNo.substring(0, 2) + "***" + residentNo.substring(residentNo.length() - 2);
    }

    private String mask(String prefix, String last4) {
        return last4 == null || last4.isBlank() ? null : prefix + last4;
    }
}
