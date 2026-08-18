package com.cunzhi.governance.system.service;

import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.system.dto.RegistrationReviewRequest;
import com.cunzhi.governance.system.dto.UserCreateRequest;
import com.cunzhi.governance.system.dto.UserDetail;
import com.cunzhi.governance.system.dto.UserRolesRequest;
import com.cunzhi.governance.system.dto.UserStatusRequest;
import com.cunzhi.governance.system.dto.UserPasswordResetRequest;
import com.cunzhi.governance.system.dto.UserSummary;
import com.cunzhi.governance.system.dto.UserUpdateRequest;
import com.cunzhi.governance.system.mapper.SystemUserMapper;
import com.cunzhi.governance.system.security.RoleCodes;
import com.cunzhi.governance.system.security.PermissionCodes;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SystemUserService {

    private final SystemUserMapper systemUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final DataScopeService dataScopeService;

    public SystemUserService(
            SystemUserMapper systemUserMapper,
            PasswordEncoder passwordEncoder,
            DataScopeService dataScopeService
    ) {
        this.systemUserMapper = systemUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.dataScopeService = dataScopeService;
    }

    public UserDetail findById(String id) {
        return toDetail(requireUser(IdParser.parse(id, "用户ID")));
    }

    @Transactional
    public UserDetail create(UserCreateRequest request) {
        if (systemUserMapper.countByUsername(request.username()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        List<String> roles = normalizeRoles(request.roleCodes());
        requireStaffRoles(roles);
        requireEnabledRoles(roles);
        systemUserMapper.insertUser(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.realName().trim(),
                normalizePhone(request.phone())
        );
        Long userId = systemUserMapper.findIdByUsername(request.username());
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建用户后未能读取用户");
        }
        roles.forEach(role -> systemUserMapper.insertUserRole(userId, role));
        return toDetail(requireUser(userId));
    }

    @Transactional
    public UserDetail update(String id, UserUpdateRequest request) {
        long userId = IdParser.parse(id, "用户ID");
        requireUser(userId);
        ensureUpdated(systemUserMapper.updateProfile(
                userId,
                request.realName().trim(),
                normalizePhone(request.phone()),
                request.version()
        ));
        return toDetail(requireUser(userId));
    }

    @Transactional
    public UserDetail updateStatus(String id, UserStatusRequest request) {
        long userId = IdParser.parse(id, "用户ID");
        SystemUserMapper.SystemUserDetailRow user = requireUser(userId);
        if (request.enabled() && !"APPROVED".equals(user.approvalStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "注册申请审核通过后才能启用账号");
        }
        if (request.enabled()
                && "RESIDENT".equals(user.accountType())
                && systemUserMapper.countActiveLinkedResident(userId) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅绑定有效居民档案的账号可以启用");
        }
        String target = request.enabled() ? "ENABLED" : "DISABLED";
        if (!request.enabled()) {
            AuthenticatedUser current = dataScopeService.currentUser();
            if (current.id().equals(userId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "不能停用当前登录账号");
            }
            ensureNotLastAdmin(userId, parseRoles(user.roleCodes()));
            ensureNoActiveWork(userId);
        }
        ensureUpdated(systemUserMapper.updateStatus(userId, target, request.version()));
        return toDetail(requireUser(userId));
    }

    @Transactional
    public UserDetail replaceRoles(String id, UserRolesRequest request) {
        long userId = IdParser.parse(id, "用户ID");
        SystemUserMapper.SystemUserDetailRow user = requireUser(userId);
        if (dataScopeService.currentUser().id().equals(userId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "不能修改当前登录账号的角色");
        }
        List<String> oldRoles = parseRoles(user.roleCodes());
        List<String> newRoles = normalizeRoles(request.roleCodes());
        if ("RESIDENT".equals(user.accountType())) {
            throw new BusinessException(ErrorCode.CONFLICT, "居民账号角色由注册审核流程维护");
        }
        requireStaffRoles(newRoles);
        requireEnabledRoles(newRoles);
        ensureRoleRemovalIsSafe(userId, oldRoles, Set.copyOf(newRoles));
        if (oldRoles.contains(RoleCodes.SYSTEM_ADMIN) && !newRoles.contains(RoleCodes.SYSTEM_ADMIN)) {
            ensureNotLastAdmin(userId, oldRoles);
        }
        ensureUpdated(systemUserMapper.touchVersion(userId, request.version()));
        systemUserMapper.endUserRoles(userId);
        newRoles.forEach(role -> systemUserMapper.insertUserRole(userId, role));
        return toDetail(requireUser(userId));
    }

    @Transactional
    public void resetPassword(String id, UserPasswordResetRequest request) {
        if (!dataScopeService.currentUser().permissions().contains(PermissionCodes.SYSTEM_USER_MANAGE)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        long userId = IdParser.parse(id, "用户ID");
        requireUser(userId);
        ensureUpdated(systemUserMapper.resetPassword(
                userId,
                passwordEncoder.encode(request.temporaryPassword()),
                request.version()
        ));
    }

    @Transactional
    public UserDetail reviewRegistration(String id, RegistrationReviewRequest request) {
        long userId = IdParser.parse(id, "用户ID");
        SystemUserMapper.SystemUserDetailRow user = requireUser(userId);
        if (!"PENDING".equals(user.approvalStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该注册申请已经处理");
        }

        long reviewerId = dataScopeService.currentUser().id();
        if ("REJECT".equals(request.decision())) {
            String reason = request.reason() == null ? null : request.reason().trim();
            if (reason == null || reason.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "驳回注册申请必须填写原因");
            }
            ensureUpdated(systemUserMapper.reviewRegistration(
                    userId, "REJECTED", "DISABLED", reviewerId, reason, request.version()
            ));
            return toDetail(requireUser(userId));
        }

        if ("RESIDENT".equals(user.accountType())) {
            if (user.requestedResidentId() == null
                    || systemUserMapper.countAvailableResident(user.requestedResidentId()) != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "居民档案已被绑定或当前不可用");
            }
            requireEnabledRoles(List.of(RoleCodes.RESIDENT));
            ensureUpdated(systemUserMapper.linkResidentUser(user.requestedResidentId(), userId));
            systemUserMapper.insertUserRole(userId, RoleCodes.RESIDENT);
        } else {
            List<String> roles = normalizeRoles(request.roleCodes());
            requireStaffRoles(roles);
            requireEnabledRoles(roles);
            roles.forEach(role -> systemUserMapper.insertUserRole(userId, role));
        }
        ensureUpdated(systemUserMapper.reviewRegistration(
                userId, "APPROVED", "ENABLED", reviewerId, null, request.version()
        ));
        return toDetail(requireUser(userId));
    }

    public PageResponse<UserSummary> findPage(String keyword, int page, int size) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        List<UserSummary> items = systemUserMapper.findPage(
                        normalizedKeyword,
                        (page - 1) * size,
                        size
                ).stream()
                .map(this::toSummary)
                .toList();
        return new PageResponse<>(items, systemUserMapper.count(normalizedKeyword), page, size);
    }

    private UserSummary toSummary(SystemUserMapper.SystemUserRow row) {
        return new UserSummary(
                row.id().toString(),
                row.username(),
                row.realName(),
                row.status(),
                row.accountType(),
                row.approvalStatus(),
                row.requestedResidentId() == null ? null : row.requestedResidentId().toString(),
                row.requestedResidentName(),
                parseRoles(row.roleCodes()),
                row.lastLoginAt(),
                row.version()
        );
    }

    private UserDetail toDetail(SystemUserMapper.SystemUserDetailRow row) {
        return new UserDetail(
                row.id().toString(), row.username(), row.realName(), row.phone(), row.status(),
                row.accountType(), row.approvalStatus(),
                row.requestedResidentId() == null ? null : row.requestedResidentId().toString(),
                row.requestedResidentName(), row.registrationNote(), row.rejectionReason(), row.reviewedAt(),
                parseRoles(row.roleCodes()), row.lastLoginAt(), row.version()
        );
    }

    private SystemUserMapper.SystemUserDetailRow requireUser(long id) {
        SystemUserMapper.SystemUserDetailRow row = systemUserMapper.findById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return row;
    }

    private List<String> normalizeRoles(Set<String> roleCodes) {
        return roleCodes.stream().map(String::trim).sorted().toList();
    }

    private void requireEnabledRoles(List<String> roles) {
        if (roles.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户至少需要一个角色");
        }
        if (systemUserMapper.countEnabledRoles(roles) != roles.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "包含不存在或已停用的角色");
        }
    }

    private void requireStaffRoles(List<String> roles) {
        if (roles.contains(RoleCodes.RESIDENT)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "居民角色只能通过居民注册审核绑定");
        }
    }

    private void ensureRoleRemovalIsSafe(long userId, List<String> oldRoles, Set<String> newRoles) {
        if (oldRoles.contains(RoleCodes.GRID_WORKER)
                && !newRoles.contains(RoleCodes.GRID_WORKER)
                && systemUserMapper.countActiveAssignments(userId, "GRID_WORKER") > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户仍有有效网格责任区，不能移除网格员角色");
        }
        if (oldRoles.contains(RoleCodes.COMMUNITY_STAFF)
                && !newRoles.contains(RoleCodes.COMMUNITY_STAFF)
                && systemUserMapper.countActiveAssignments(userId, "COMMUNITY_STAFF") > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户仍有有效社区责任区，不能移除社区工作人员角色");
        }
    }

    private void ensureNotLastAdmin(long userId, List<String> roles) {
        if (roles.contains(RoleCodes.SYSTEM_ADMIN)
                && systemUserMapper.countOtherEnabledSystemAdmins(userId) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "必须至少保留一个可用系统管理员");
        }
    }

    private void ensureNoActiveWork(long userId) {
        if (systemUserMapper.countActiveAssignments(userId, RoleCodes.GRID_WORKER) > 0
                || systemUserMapper.countActiveAssignments(userId, RoleCodes.COMMUNITY_STAFF) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户仍有有效责任区，不能停用");
        }
        if (systemUserMapper.countOpenAssignedTasks(userId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户仍有未终止任务，不能停用");
        }
    }

    private String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }

    private List<String> parseRoles(String roleCodes) {
        return roleCodes == null || roleCodes.isBlank()
                ? List.of()
                : new ArrayList<>(Arrays.asList(roleCodes.split(",")));
    }

    private void ensureUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }
}
