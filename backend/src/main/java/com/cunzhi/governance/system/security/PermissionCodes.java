package com.cunzhi.governance.system.security;

public final class PermissionCodes {

    public static final String SYSTEM_USER_MANAGE = "system:user:manage";
    public static final String SYSTEM_ROLE_MANAGE = "system:role:manage";
    public static final String SYSTEM_MENU_MANAGE = "system:menu:manage";
    public static final String GRID_READ = "grid:read";
    public static final String GRID_WRITE = "grid:write";
    public static final String GRID_ASSIGN = "grid:assign";
    public static final String RESIDENT_READ = "resident:read";
    public static final String RESIDENT_WRITE = "resident:write";
    public static final String RESIDENT_SENSITIVE_READ = "resident:sensitive:read";
    public static final String RESIDENT_SENSITIVE_AUDIT_READ = "resident:sensitive:audit:read";
    public static final String RESIDENT_PORTAL = "resident:portal";
    public static final String EVENT_READ = "event:read";
    public static final String EVENT_REPORT = "event:report";
    public static final String EVENT_ACCEPT = "event:accept";
    public static final String EVENT_REJECT = "event:reject";
    public static final String EVENT_ASSIGN = "event:assign";
    public static final String EVENT_CANCEL = "event:cancel";
    public static final String EVENT_CATEGORY_MANAGE = "event:category:manage";
    public static final String TASK_READ = "task:read";
    public static final String TASK_CREATE = "task:create";
    public static final String TASK_ACCEPT = "task:accept";
    public static final String TASK_HANDLE = "task:handle";
    public static final String TASK_REVIEW = "task:review";
    public static final String TASK_CANCEL = "task:cancel";
    public static final String FILE_READ = "file:read";
    public static final String FILE_UPLOAD = "file:upload";
    public static final String FILE_DELETE = "file:delete";
    public static final String DASHBOARD_READ = "dashboard:read";

    private PermissionCodes() {
    }
}
