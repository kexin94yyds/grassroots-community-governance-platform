package com.cunzhi.governance.system.security;

import java.util.Set;

public record DataScope(
        DataScopeType type,
        Set<Long> gridIds
) {
    public DataScope {
        gridIds = Set.copyOf(gridIds);
    }

    public static DataScope all() {
        return new DataScope(DataScopeType.ALL, Set.of());
    }

    public boolean allows(long gridId) {
        return type == DataScopeType.ALL || gridIds.contains(gridId);
    }
}
