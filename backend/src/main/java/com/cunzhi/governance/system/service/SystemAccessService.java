package com.cunzhi.governance.system.service;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.system.dto.MenuItem;
import com.cunzhi.governance.system.dto.MenuUpdateRequest;
import com.cunzhi.governance.system.dto.RoleOption;
import com.cunzhi.governance.system.dto.RoleUpdateRequest;
import com.cunzhi.governance.system.mapper.SystemAccessMapper;
import com.cunzhi.governance.system.security.PermissionCodes;
import com.cunzhi.governance.system.security.RoleCodes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SystemAccessService {

    private static final Set<String> CORE_ROLES = Set.of(
            RoleCodes.SYSTEM_ADMIN,
            RoleCodes.COMMUNITY_STAFF,
            RoleCodes.GRID_WORKER,
            RoleCodes.RESIDENT
    );
    private static final Set<String> PROTECTED_MENUS = Set.of(
            "SYSTEM_USER", "SYSTEM_ROLE", "SYSTEM_MENU", "EVENT_CATEGORY", "RESIDENT_PORTAL"
    );
    private static final Set<String> GRID_WORKER_PERMISSIONS = Set.of(
            PermissionCodes.DASHBOARD_READ,
            PermissionCodes.GRID_READ,
            PermissionCodes.RESIDENT_READ,
            PermissionCodes.EVENT_READ,
            PermissionCodes.EVENT_REPORT,
            PermissionCodes.TASK_READ,
            PermissionCodes.TASK_ACCEPT,
            PermissionCodes.TASK_HANDLE,
            PermissionCodes.FILE_READ,
            PermissionCodes.FILE_UPLOAD,
            PermissionCodes.FILE_DELETE,
            PermissionCodes.WORKBENCH_GRID_READ,
            PermissionCodes.ANNOUNCEMENT_READ,
            PermissionCodes.PATROL_READ
    );
    private static final Set<String> REQUIRED_ADMIN_PERMISSIONS = Set.of(
            PermissionCodes.SYSTEM_USER_MANAGE,
            PermissionCodes.SYSTEM_ROLE_MANAGE,
            PermissionCodes.SYSTEM_MENU_MANAGE,
            PermissionCodes.EVENT_CATEGORY_MANAGE
    );
    private static final Set<String> ADMIN_ALLOWED_PERMISSIONS = Set.of(
            PermissionCodes.DASHBOARD_READ,
            PermissionCodes.SYSTEM_USER_MANAGE,
            PermissionCodes.SYSTEM_ROLE_MANAGE,
            PermissionCodes.SYSTEM_MENU_MANAGE,
            PermissionCodes.GRID_READ,
            PermissionCodes.GRID_WRITE,
            PermissionCodes.GRID_ASSIGN,
            PermissionCodes.RESIDENT_READ,
            PermissionCodes.RESIDENT_WRITE,
            PermissionCodes.RESIDENT_SENSITIVE_READ,
            PermissionCodes.RESIDENT_SENSITIVE_AUDIT_READ,
            PermissionCodes.EVENT_READ,
            PermissionCodes.EVENT_REPORT,
            PermissionCodes.EVENT_ACCEPT,
            PermissionCodes.EVENT_REJECT,
            PermissionCodes.EVENT_ASSIGN,
            PermissionCodes.EVENT_CANCEL,
            PermissionCodes.EVENT_CATEGORY_MANAGE,
            PermissionCodes.TASK_READ,
            PermissionCodes.TASK_CREATE,
            PermissionCodes.TASK_REVIEW,
            PermissionCodes.TASK_CANCEL,
            PermissionCodes.FILE_READ,
            PermissionCodes.FILE_UPLOAD,
            PermissionCodes.FILE_DELETE,
            PermissionCodes.WORKBENCH_ADMIN_READ,
            PermissionCodes.ANNOUNCEMENT_READ,
            PermissionCodes.ANNOUNCEMENT_GLOBAL_WRITE,
            PermissionCodes.SERVICE_CATALOG_READ,
            PermissionCodes.SERVICE_CATALOG_MANAGE,
            PermissionCodes.SERVICE_APPLICATION_READ,
            PermissionCodes.PATROL_READ,
            PermissionCodes.SYSTEM_AUDIT_READ,
            PermissionCodes.SYSTEM_HEALTH_READ
    );
    private static final Set<String> COMMUNITY_STAFF_PERMISSIONS = Set.of(
            PermissionCodes.DASHBOARD_READ,
            PermissionCodes.GRID_READ,
            PermissionCodes.GRID_WRITE,
            PermissionCodes.GRID_ASSIGN,
            PermissionCodes.RESIDENT_READ,
            PermissionCodes.RESIDENT_WRITE,
            PermissionCodes.RESIDENT_SENSITIVE_READ,
            PermissionCodes.RESIDENT_SENSITIVE_AUDIT_READ,
            PermissionCodes.EVENT_READ,
            PermissionCodes.EVENT_REPORT,
            PermissionCodes.EVENT_ACCEPT,
            PermissionCodes.EVENT_REJECT,
            PermissionCodes.EVENT_ASSIGN,
            PermissionCodes.EVENT_CANCEL,
            PermissionCodes.TASK_READ,
            PermissionCodes.TASK_CREATE,
            PermissionCodes.TASK_REVIEW,
            PermissionCodes.TASK_CANCEL,
            PermissionCodes.FILE_READ,
            PermissionCodes.FILE_UPLOAD,
            PermissionCodes.FILE_DELETE,
            PermissionCodes.WORKBENCH_COMMUNITY_READ,
            PermissionCodes.ANNOUNCEMENT_READ,
            PermissionCodes.ANNOUNCEMENT_COMMUNITY_WRITE,
            PermissionCodes.SERVICE_CATALOG_READ,
            PermissionCodes.SERVICE_APPLICATION_READ,
            PermissionCodes.SERVICE_APPLICATION_HANDLE,
            PermissionCodes.PATROL_READ,
            PermissionCodes.PATROL_PLAN_WRITE
    );
    private static final Set<String> RESIDENT_PERMISSIONS = Set.of(
            PermissionCodes.RESIDENT_PORTAL,
            PermissionCodes.WORKBENCH_RESIDENT_READ,
            PermissionCodes.ANNOUNCEMENT_READ,
            PermissionCodes.SERVICE_CATALOG_READ,
            PermissionCodes.SERVICE_APPLICATION_APPLY,
            PermissionCodes.SERVICE_APPLICATION_CANCEL,
            PermissionCodes.SERVICE_APPLICATION_RATE
    );

    private final SystemAccessMapper mapper;

    public SystemAccessService(SystemAccessMapper mapper) {
        this.mapper = mapper;
    }

    public List<RoleOption> findRoles() {
        return mapper.findRoles().stream().map(this::toRole).toList();
    }

    public List<MenuItem> findMenus() {
        return mapper.findMenus().stream().map(this::toMenu).toList();
    }

    @Transactional
    public RoleOption updateRole(String roleCode, RoleUpdateRequest request) {
        String code = roleCode == null ? "" : roleCode.trim();
        if (!CORE_ROLES.contains(code)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "核心角色不存在");
        }
        SystemAccessMapper.RoleRow current = requireRole(code);
        List<Long> menuIds = request.menuIds().stream()
                .map(value -> IdParser.parse(value, "菜单ID"))
                .sorted()
                .toList();
        if (mapper.countEnabledMenus(menuIds) != menuIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限配置包含不存在或已停用的菜单项");
        }
        if (mapper.countSelectedMenusWithMissingParents(menuIds) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "选择操作权限时必须同时保留所属菜单");
        }

        Map<Long, SystemAccessMapper.MenuRow> menuById = mapper.findMenus().stream()
                .collect(Collectors.toMap(SystemAccessMapper.MenuRow::id, item -> item));
        Set<String> permissions = menuIds.stream()
                .map(menuById::get)
                .filter(item -> item != null && item.permissionCode() != null)
                .map(SystemAccessMapper.MenuRow::permissionCode)
                .collect(Collectors.toSet());
        requireCompatiblePermissions(code, permissions);

        if (RoleCodes.SYSTEM_ADMIN.equals(code) && "DISABLED".equals(request.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统管理员角色不能停用");
        }
        if (!current.status().equals(request.status())
                && "DISABLED".equals(request.status())
                && mapper.countActiveUsersForRole(code) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色仍分配给启用账号，不能停用");
        }
        Set<String> currentMenuIds = current.menuIds() == null || current.menuIds().isBlank()
                ? Set.of()
                : Set.copyOf(Arrays.asList(current.menuIds().split(",")));
        boolean securityChanged = !current.status().equals(request.status())
                || !currentMenuIds.equals(request.menuIds());

        ensureUpdated(mapper.updateRole(
                code,
                request.name().trim(),
                normalizeText(request.description()),
                request.status(),
                request.version()
        ));
        mapper.deleteRoleMenus(code);
        menuIds.forEach(menuId -> mapper.insertRoleMenu(code, menuId));
        if (securityChanged) {
            mapper.bumpUserSecurityVersionsForRole(code);
        }
        return toRole(requireRole(code));
    }

    @Transactional
    public MenuItem updateMenu(String idValue, MenuUpdateRequest request) {
        long id = IdParser.parse(idValue, "菜单ID");
        SystemAccessMapper.MenuRow current = requireMenu(id);
        if (PROTECTED_MENUS.contains(current.code()) && "DISABLED".equals(request.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该核心入口不能停用");
        }
        if ("ENABLED".equals(current.status())
                && "DISABLED".equals(request.status())
                && mapper.countEnabledChildren(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先停用该菜单下的操作权限");
        }
        ensureUpdated(mapper.updateMenu(
                id,
                request.name().trim(),
                normalizeText(request.icon()),
                request.sortNo(),
                request.status(),
                request.version()
        ));
        if (!current.status().equals(request.status())) {
            mapper.bumpUserSecurityVersionsForMenu(id);
        }
        return toMenu(requireMenu(id));
    }

    private void requireCompatiblePermissions(String roleCode, Set<String> permissions) {
        if (RoleCodes.SYSTEM_ADMIN.equals(roleCode)) {
            if (!permissions.containsAll(REQUIRED_ADMIN_PERMISSIONS)
                    || !ADMIN_ALLOWED_PERMISSIONS.containsAll(permissions)
                    || permissions.contains(PermissionCodes.TASK_ACCEPT)
                    || permissions.contains(PermissionCodes.TASK_HANDLE)
                    || permissions.contains(PermissionCodes.RESIDENT_PORTAL)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统管理员必须保留系统管理权限，且不能承担任务处置或居民权限");
            }
            return;
        }
        if (RoleCodes.COMMUNITY_STAFF.equals(roleCode)) {
            if (!COMMUNITY_STAFF_PERMISSIONS.containsAll(permissions)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "社区工作人员权限超出固定职责边界");
            }
            return;
        }
        if (RoleCodes.GRID_WORKER.equals(roleCode) && !GRID_WORKER_PERMISSIONS.containsAll(permissions)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "网格员权限超出固定职责边界");
        }
        if (RoleCodes.RESIDENT.equals(roleCode)
                && (!RESIDENT_PERMISSIONS.containsAll(permissions)
                || !permissions.contains(PermissionCodes.RESIDENT_PORTAL))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "居民角色只能保留居民服务台权限");
        }
    }

    private SystemAccessMapper.RoleRow requireRole(String code) {
        SystemAccessMapper.RoleRow row = mapper.findRole(code);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return row;
    }

    private SystemAccessMapper.MenuRow requireMenu(long id) {
        SystemAccessMapper.MenuRow row = mapper.findMenu(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "菜单不存在");
        }
        return row;
    }

    private RoleOption toRole(SystemAccessMapper.RoleRow row) {
        List<String> menuIds = row.menuIds() == null || row.menuIds().isBlank()
                ? List.of()
                : Arrays.asList(row.menuIds().split(","));
        return new RoleOption(row.code(), row.name(), row.description(), row.status(), menuIds, row.version());
    }

    private MenuItem toMenu(SystemAccessMapper.MenuRow row) {
        return new MenuItem(
                row.id().toString(),
                row.parentId() == null ? null : row.parentId().toString(),
                row.code(), row.name(), row.type(), row.routePath(), row.permissionCode(),
                row.icon(), row.sortNo(), row.status(), row.version()
        );
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }
}
