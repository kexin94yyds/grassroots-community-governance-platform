package com.cunzhi.governance.serviceapplication.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.resident.mapper.ResidentMapper;
import com.cunzhi.governance.serviceapplication.domain.ServiceApplicationStatus;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationActionRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationCreateRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationFlowView;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationRateRequest;
import com.cunzhi.governance.serviceapplication.dto.ServiceApplicationView;
import com.cunzhi.governance.serviceapplication.mapper.ServiceApplicationFlowMapper;
import com.cunzhi.governance.serviceapplication.mapper.ServiceApplicationMapper;
import com.cunzhi.governance.serviceapplication.mapper.ServiceCatalogMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.security.PermissionCodes;
import com.cunzhi.governance.system.security.RoleCodes;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.service.DataScopeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceApplicationService {

    private final ServiceApplicationMapper applicationMapper;
    private final ServiceApplicationFlowMapper flowMapper;
    private final ServiceCatalogMapper catalogMapper;
    private final ResidentMapper residentMapper;
    private final DataScopeMapper dataScopeMapper;
    private final DataScopeService dataScopeService;
    private final BusinessNumberGenerator numberGenerator;

    public ServiceApplicationService(
            ServiceApplicationMapper applicationMapper,
            ServiceApplicationFlowMapper flowMapper,
            ServiceCatalogMapper catalogMapper,
            ResidentMapper residentMapper,
            DataScopeMapper dataScopeMapper,
            DataScopeService dataScopeService,
            BusinessNumberGenerator numberGenerator
    ) {
        this.applicationMapper = applicationMapper;
        this.flowMapper = flowMapper;
        this.catalogMapper = catalogMapper;
        this.residentMapper = residentMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.dataScopeService = dataScopeService;
        this.numberGenerator = numberGenerator;
    }

    public PageResponse<ServiceApplicationView> findScoped(String status, int page, int size) {
        DataScope scope = requireStaffReadScope();
        String normalizedStatus = normalizeStatus(status);
        boolean allAccess = scope.type() == DataScopeType.ALL;
        List<Long> gridIds = new ArrayList<>(scope.gridIds());
        List<ServiceApplicationView> items = applicationMapper.findPageScoped(
                        normalizedStatus, allAccess, gridIds, (page - 1) * size, size
                ).stream()
                .map(this::toView)
                .toList();
        return new PageResponse<>(items,
                applicationMapper.countScoped(normalizedStatus, allAccess, gridIds), page, size);
    }

    public ServiceApplicationView findScopedById(String idValue) {
        ServiceApplicationMapper.ApplicationRow row = requireApplication(IdParser.parse(idValue, "服务申请ID"));
        requireStaffScope(row.gridId());
        return toView(row);
    }

    public List<ServiceApplicationFlowView> findScopedFlows(String idValue) {
        long id = IdParser.parse(idValue, "服务申请ID");
        ServiceApplicationMapper.ApplicationRow row = requireApplication(id);
        requireStaffScope(row.gridId());
        return flowMapper.findByApplicationId(id).stream().map(item -> new ServiceApplicationFlowView(
                item.id().toString(), item.action(), item.fromStatus(), item.toStatus(),
                item.operatorUserId().toString(), item.operatorName(), item.remark(), item.createdAt()
        )).toList();
    }

    public List<ServiceApplicationView> findForCurrentResident() {
        AuthenticatedUser user = requireResidentPermission(PermissionCodes.RESIDENT_PORTAL);
        return applicationMapper.findByApplicantUserId(user.id()).stream().map(this::toView).toList();
    }

    @Transactional
    public ServiceApplicationView apply(ServiceApplicationCreateRequest request) {
        AuthenticatedUser user = requireResidentPermission(PermissionCodes.SERVICE_APPLICATION_APPLY);
        String requestToken = normalizeRequestToken(request.requestToken());
        if (requestToken != null) {
            ServiceApplicationMapper.ApplicationRow existing = applicationMapper.findByApplicantAndRequestToken(user.id(), requestToken);
            if (existing != null) {
                return toView(existing);
            }
        }
        ResidentMapper.ResidentRow resident = residentMapper.findByUserId(user.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "当前账号未绑定有效居民档案"));
        long catalogId = IdParser.parse(request.serviceCatalogId(), "服务目录ID");
        if (catalogMapper.findEnabledByIdForUpdate(catalogId) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "服务目录不存在或已停用");
        }
        String applicationNo = numberGenerator.next("SVC");
        try {
            ensureUpdated(applicationMapper.insert(
                    applicationNo, resident.id(), user.id(), resident.gridId(), catalogId,
                    request.requestContent().trim(), request.appointmentAt(), requestToken
            ));
        } catch (DuplicateKeyException exception) {
            if (requestToken != null) {
                ServiceApplicationMapper.ApplicationRow existing = applicationMapper.findByApplicantAndRequestToken(user.id(), requestToken);
                if (existing != null) {
                    return toView(existing);
                }
            }
            throw new BusinessException(ErrorCode.CONFLICT, "服务申请提交令牌已被占用");
        }
        Long id = applicationMapper.findIdByApplicationNo(applicationNo);
        if (id == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "提交服务申请后未能读取记录");
        }
        ensureUpdated(flowMapper.insert(id, "APPLY", null, ServiceApplicationStatus.SUBMITTED.name(), user.id(), "提交服务申请"));
        return toView(requireApplication(id));
    }

    @Transactional
    public ServiceApplicationView accept(String idValue, ServiceApplicationActionRequest request) {
        return handleTransition(idValue, request, ServiceApplicationStatus.ACCEPTED, "ACCEPT", true, false);
    }

    @Transactional
    public ServiceApplicationView start(String idValue, ServiceApplicationActionRequest request) {
        return handleTransition(idValue, request, ServiceApplicationStatus.PROCESSING, "START", false, false);
    }

    @Transactional
    public ServiceApplicationView complete(String idValue, ServiceApplicationActionRequest request) {
        if (normalize(request.resultSummary()) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "完成服务申请必须填写处理结果");
        }
        return handleTransition(idValue, request, ServiceApplicationStatus.COMPLETED, "COMPLETE", false, true);
    }

    @Transactional
    public ServiceApplicationView reject(String idValue, ServiceApplicationActionRequest request) {
        if (normalize(request.remark()) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "驳回服务申请必须填写原因");
        }
        return handleTransition(idValue, request, ServiceApplicationStatus.REJECTED, "REJECT", false, false);
    }

    @Transactional
    public ServiceApplicationView cancel(String idValue, ServiceApplicationActionRequest request) {
        AuthenticatedUser user = requireResidentPermission(PermissionCodes.SERVICE_APPLICATION_CANCEL);
        long id = IdParser.parse(idValue, "服务申请ID");
        ServiceApplicationMapper.ApplicationLockRow application = requireLockedApplication(id);
        requireResidentOwnership(application, user);
        ServiceApplicationStatus from = ServiceApplicationStatus.valueOf(application.status());
        from.requireTransitionTo(ServiceApplicationStatus.CANCELLED);
        if (normalize(request.remark()) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "撤回服务申请必须填写原因");
        }
        ensureUpdated(applicationMapper.transition(
                id, from.name(), ServiceApplicationStatus.CANCELLED.name(), null, null, request.version()
        ));
        ensureUpdated(flowMapper.insert(id, "CANCEL", from.name(), ServiceApplicationStatus.CANCELLED.name(),
                user.id(), normalize(request.remark())));
        return toView(requireApplication(id));
    }

    @Transactional
    public ServiceApplicationView rate(String idValue, ServiceApplicationRateRequest request) {
        AuthenticatedUser user = requireResidentPermission(PermissionCodes.SERVICE_APPLICATION_RATE);
        long id = IdParser.parse(idValue, "服务申请ID");
        ServiceApplicationMapper.ApplicationLockRow application = requireLockedApplication(id);
        requireResidentOwnership(application, user);
        if (!ServiceApplicationStatus.COMPLETED.name().equals(application.status()) || application.rating() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "仅可对未评分的已完成服务申请评分一次");
        }
        ensureUpdated(applicationMapper.rate(id, user.id(), request.rating(), normalize(request.remark()), request.version()));
        ensureUpdated(flowMapper.insert(id, "RATE", application.status(), application.status(), user.id(),
                "评分：" + request.rating()));
        return toView(requireApplication(id));
    }

    private ServiceApplicationView handleTransition(
            String idValue,
            ServiceApplicationActionRequest request,
            ServiceApplicationStatus target,
            String action,
            boolean assignHandler,
            boolean writeResult
    ) {
        AuthenticatedUser operator = requireCommunityHandler();
        long id = IdParser.parse(idValue, "服务申请ID");
        ServiceApplicationMapper.ApplicationLockRow application;
        if (assignHandler) {
            ServiceApplicationMapper.ApplicationRow snapshot = requireApplication(id);
            lockCommunityAssignmentForAccept(operator, snapshot.gridId());
            application = requireLockedApplication(id);
            if (!application.gridId().equals(snapshot.gridId())) {
                throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
            }
        } else {
            application = requireLockedApplication(id);
            requireStaffScope(application.gridId());
        }
        boolean requiresExistingHandler = !assignHandler && target != ServiceApplicationStatus.REJECTED;
        if (requiresExistingHandler
                && (application.handlerUserId() == null || !application.handlerUserId().equals(operator.id()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅当前服务处理人可以执行该动作");
        }
        ServiceApplicationStatus from = ServiceApplicationStatus.valueOf(application.status());
        from.requireTransitionTo(target);
        ensureUpdated(applicationMapper.transition(
                id, from.name(), target.name(), assignHandler ? operator.id() : null,
                writeResult ? normalize(request.resultSummary()) : null, request.version()
        ));
        ensureUpdated(flowMapper.insert(id, action, from.name(), target.name(), operator.id(), normalize(request.remark())));
        return toView(requireApplication(id));
    }

    private DataScope requireStaffReadScope() {
        AuthenticatedUser user = dataScopeService.currentUser();
        if (user.roles().contains(RoleCodes.SYSTEM_ADMIN)) {
            return DataScope.all();
        }
        if (!user.roles().contains(RoleCodes.COMMUNITY_STAFF)
                || !user.permissions().contains(PermissionCodes.SERVICE_APPLICATION_READ)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看服务申请");
        }
        return dataScopeService.scopeForRole(RoleCodes.COMMUNITY_STAFF);
    }

    private AuthenticatedUser requireCommunityHandler() {
        AuthenticatedUser user = dataScopeService.currentUser();
        if (!user.roles().contains(RoleCodes.COMMUNITY_STAFF)
                || !user.permissions().contains(PermissionCodes.SERVICE_APPLICATION_HANDLE)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅社区工作人员可处理服务申请");
        }
        return user;
    }

    private AuthenticatedUser requireResidentPermission(String permission) {
        AuthenticatedUser user = dataScopeService.currentUser();
        if (!user.roles().contains(RoleCodes.RESIDENT) || !user.permissions().contains(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅居民账号可执行该操作");
        }
        return user;
    }

    private void requireStaffScope(long gridId) {
        DataScope scope = requireStaffReadScope();
        if (!scope.allows(gridId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该网格的服务申请");
        }
    }

    private void requireResidentOwnership(ServiceApplicationMapper.ApplicationLockRow application, AuthenticatedUser user) {
        if (!application.applicantUserId().equals(user.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可操作本人服务申请");
        }
    }

    private void lockCommunityAssignmentForAccept(AuthenticatedUser user, long gridId) {
        Long communityId = dataScopeMapper.findParentCommunityId(gridId);
        if (communityId == null || dataScopeMapper.lockEnabledCommunityForUpdate(communityId) == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "服务申请所属社区不可用");
        }
        if (dataScopeMapper.lockActiveCommunityStaffAssignmentForUpdate(user.id(), communityId) == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号已不具备该社区处理范围");
        }
    }

    private ServiceApplicationMapper.ApplicationRow requireApplication(long id) {
        return applicationMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "服务申请不存在"));
    }

    private ServiceApplicationMapper.ApplicationLockRow requireLockedApplication(long id) {
        ServiceApplicationMapper.ApplicationLockRow row = applicationMapper.findByIdForUpdate(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务申请不存在");
        }
        return row;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ServiceApplicationStatus.valueOf(status.trim()).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知服务申请状态");
        }
    }

    private String normalizeRequestToken(String requestToken) {
        if (requestToken == null || requestToken.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(requestToken).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "requestToken 必须是标准 UUID");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private ServiceApplicationView toView(ServiceApplicationMapper.ApplicationRow row) {
        return new ServiceApplicationView(
                row.id().toString(), row.applicationNo(), row.serviceCatalogId().toString(), row.serviceCatalogName(),
                row.residentId().toString(), row.residentName(), row.gridId().toString(), row.gridName(),
                row.requestContent(), row.appointmentAt(), row.status(),
                row.handlerUserId() == null ? null : row.handlerUserId().toString(), row.handlerName(),
                row.resultSummary(), row.rating(), row.ratingRemark(), row.createdAt(), row.completedAt(), row.version()
        );
    }
}
