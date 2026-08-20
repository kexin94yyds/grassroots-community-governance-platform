delete role_menu
from sys_role_menu role_menu
join sys_role role on role.id = role_menu.role_id
join sys_menu menu on menu.id = role_menu.menu_id
where (role.role_code = 'SYSTEM_ADMIN'
       and menu.menu_code in (
         'ANNOUNCEMENT', 'ANNOUNCEMENT_GLOBAL_WRITE',
         'SERVICE_CATALOG', 'SERVICE_CATALOG_MANAGE', 'SERVICE_APPLICATION_READ',
         'PATROL_READ', 'SYSTEM_HEALTH'
       ))
   or (role.role_code = 'COMMUNITY_STAFF'
       and menu.menu_code in (
         'COMMUNITY_TODO', 'COMMUNITY_SERVICE', 'COMMUNITY_PATROL', 'COMMUNITY_REPORT',
         'ANNOUNCEMENT', 'ANNOUNCEMENT_COMMUNITY_WRITE',
         'SERVICE_CATALOG_READ', 'SERVICE_APPLICATION_READ', 'SERVICE_APPLICATION_HANDLE',
         'PATROL_READ', 'PATROL_PLAN_WRITE'
       ))
   or (role.role_code = 'GRID_WORKER'
       and menu.menu_code in ('GRID_PATROL', 'ANNOUNCEMENT', 'PATROL_READ'))
   or (role.role_code = 'RESIDENT'
       and menu.menu_code in (
         'RESIDENT_SERVICE', 'RESIDENT_RATING', 'ANNOUNCEMENT', 'SERVICE_CATALOG_READ',
         'SERVICE_APPLICATION_APPLY', 'SERVICE_APPLICATION_CANCEL', 'SERVICE_APPLICATION_RATE'
       ));
