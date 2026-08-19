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
    public static final String WORKBENCH_ADMIN_READ = "workbench:admin:read";
    public static final String WORKBENCH_COMMUNITY_READ = "workbench:community:read";
    public static final String WORKBENCH_GRID_READ = "workbench:grid:read";
    public static final String WORKBENCH_RESIDENT_READ = "workbench:resident:read";
    public static final String ANNOUNCEMENT_READ = "announcement:read";
    public static final String ANNOUNCEMENT_GLOBAL_WRITE = "announcement:global:write";
    public static final String ANNOUNCEMENT_COMMUNITY_WRITE = "announcement:community:write";
    public static final String SERVICE_CATALOG_READ = "service:catalog:read";
    public static final String SERVICE_CATALOG_MANAGE = "service:catalog:manage";
    public static final String SERVICE_APPLICATION_READ = "service:application:read";
    public static final String SERVICE_APPLICATION_HANDLE = "service:application:handle";
    public static final String SERVICE_APPLICATION_APPLY = "service:application:apply";
    public static final String SERVICE_APPLICATION_CANCEL = "service:application:cancel";
    public static final String SERVICE_APPLICATION_RATE = "service:application:rate";
    public static final String PATROL_READ = "patrol:read";
    public static final String PATROL_PLAN_WRITE = "patrol:plan:write";
    public static final String SYSTEM_AUDIT_READ = "system:audit:read";
    public static final String SYSTEM_HEALTH_READ = "system:health:read";

    private PermissionCodes() {
    }
}
