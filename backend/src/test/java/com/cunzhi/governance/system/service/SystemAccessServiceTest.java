package com.cunzhi.governance.system.service;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.system.dto.MenuUpdateRequest;
import com.cunzhi.governance.system.dto.RoleUpdateRequest;
import com.cunzhi.governance.system.mapper.SystemAccessMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAccessServiceTest {

    @Mock
    private SystemAccessMapper mapper;

    @Test
    void systemAdministratorCannotLoseRequiredManagementPermissions() {
        when(mapper.findRole("SYSTEM_ADMIN")).thenReturn(role("SYSTEM_ADMIN", "100", 2));
        when(mapper.countEnabledMenus(List.of(100L))).thenReturn(1);
        when(mapper.countSelectedMenusWithMissingParents(List.of(100L))).thenReturn(0);
        when(mapper.findMenus()).thenReturn(List.of(menu(100, null, "DASHBOARD", "dashboard:read", 1)));

        assertThatThrownBy(() -> service().updateRole(
                "SYSTEM_ADMIN",
                new RoleUpdateRequest("系统管理员", null, "ENABLED", Set.of("100"), 2)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(mapper, never()).updateRole("SYSTEM_ADMIN", "系统管理员", null, "ENABLED", 2);
    }

    @Test
    void changingCommunityStaffPermissionsReplacesMappingsAndInvalidatesSessions() {
        var current = role("COMMUNITY_STAFF", "300", 2);
        var updated = role("COMMUNITY_STAFF", "300,1001", 3);
        when(mapper.findRole("COMMUNITY_STAFF")).thenReturn(current, updated);
        when(mapper.countEnabledMenus(List.of(300L, 1001L))).thenReturn(2);
        when(mapper.countSelectedMenusWithMissingParents(List.of(300L, 1001L))).thenReturn(0);
        when(mapper.findMenus()).thenReturn(List.of(
                menu(300, null, "GRID", "grid:read", 1),
                menu(1001, 300L, "GRID_WRITE", "grid:write", 1)
        ));
        when(mapper.updateRole("COMMUNITY_STAFF", "社区工作人员", "社区治理", "ENABLED", 2))
                .thenReturn(1);

        var result = service().updateRole(
                "COMMUNITY_STAFF",
                new RoleUpdateRequest(
                        "社区工作人员", "社区治理", "ENABLED", Set.of("300", "1001"), 2
                )
        );

        assertThat(result.menuIds()).containsExactly("300", "1001");
        verify(mapper).deleteRoleMenus("COMMUNITY_STAFF");
        verify(mapper).insertRoleMenu("COMMUNITY_STAFF", 300);
        verify(mapper).insertRoleMenu("COMMUNITY_STAFF", 1001);
        verify(mapper).bumpUserSecurityVersionsForRole("COMMUNITY_STAFF");
    }

    @Test
    void cosmeticMenuUpdateDoesNotInvalidateSessions() {
        var current = menu(500, null, "EVENT", "event:read", 4);
        var updated = new SystemAccessMapper.MenuRow(
                500L, null, "EVENT", "治理事项", "MENU", "/events",
                "event:read", "el-icon-warning-outline", 55, "ENABLED", 5
        );
        when(mapper.findMenu(500)).thenReturn(current, updated);
        when(mapper.updateMenu(500, "治理事项", "el-icon-warning-outline", 55, "ENABLED", 4))
                .thenReturn(1);

        var result = service().updateMenu(
                "500",
                new MenuUpdateRequest("治理事项", "el-icon-warning-outline", 55, "ENABLED", 4)
        );

        assertThat(result.name()).isEqualTo("治理事项");
        verify(mapper, never()).bumpUserSecurityVersionsForMenu(500);
    }

    @Test
    void protectedCoreMenuCannotBeDisabled() {
        when(mapper.findMenu(210)).thenReturn(menu(
                210, null, "SYSTEM_ROLE", "system:role:manage", 1
        ));

        assertThatThrownBy(() -> service().updateMenu(
                "210",
                new MenuUpdateRequest("角色管理", "el-icon-s-custom", 21, "DISABLED", 1)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(mapper, never()).updateMenu(210, "角色管理", "el-icon-s-custom", 21, "DISABLED", 1);
    }

    private SystemAccessService service() {
        return new SystemAccessService(mapper);
    }

    private SystemAccessMapper.RoleRow role(String code, String menuIds, int version) {
        return new SystemAccessMapper.RoleRow(
                code, "SYSTEM_ADMIN".equals(code) ? "系统管理员" : "社区工作人员",
                null, "ENABLED", version, menuIds
        );
    }

    private SystemAccessMapper.MenuRow menu(
            long id,
            Long parentId,
            String code,
            String permission,
            int version
    ) {
        return new SystemAccessMapper.MenuRow(
                id, parentId, code, code, parentId == null ? "MENU" : "ACTION",
                parentId == null ? "/" + code.toLowerCase() : null,
                permission, null, 1, "ENABLED", version
        );
    }
}
