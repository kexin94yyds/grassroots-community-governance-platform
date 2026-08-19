package com.cunzhi.governance.system.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.security.RoleCodes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;

@Service
public class DataScopeService {

    private final DataScopeMapper dataScopeMapper;

    public DataScopeService(DataScopeMapper dataScopeMapper) {
        this.dataScopeMapper = dataScopeMapper;
    }

    public DataScope currentScope() {
        AuthenticatedUser user = currentUser();
        if (user.roles().contains(RoleCodes.SYSTEM_ADMIN)) {
            return DataScope.all();
        }
        DataScopeType type = user.roles().contains(RoleCodes.COMMUNITY_STAFF)
                ? DataScopeType.COMMUNITY
                : DataScopeType.GRID;
        return new DataScope(type, new LinkedHashSet<>(dataScopeMapper.findAccessibleGridIds(user.id())));
    }

    public DataScope scopeForRole(String roleCode) {
        AuthenticatedUser user = currentUser();
        if (!user.roles().contains(roleCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号不具备该工作台角色");
        }
        if (RoleCodes.SYSTEM_ADMIN.equals(roleCode)) {
            return DataScope.all();
        }
        if (RoleCodes.COMMUNITY_STAFF.equals(roleCode)) {
            return new DataScope(DataScopeType.COMMUNITY,
                    new LinkedHashSet<>(dataScopeMapper.findCommunityGridIds(user.id())));
        }
        if (RoleCodes.GRID_WORKER.equals(roleCode)) {
            return new DataScope(DataScopeType.GRID,
                    new LinkedHashSet<>(dataScopeMapper.findGridWorkerGridIds(user.id())));
        }
        return new DataScope(DataScopeType.GRID,
                new LinkedHashSet<>(dataScopeMapper.findAccessibleGridIds(user.id())));
    }

    public void requireGridAccess(long gridId) {
        if (!currentScope().allows(gridId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该网格数据");
        }
    }

    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }
}
