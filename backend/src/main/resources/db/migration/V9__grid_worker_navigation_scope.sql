update sys_menu
set permission_code = null
where menu_code in ('GRID', 'RESIDENT')
  and menu_type = 'MENU';

insert into sys_menu (
  parent_id, menu_code, menu_name, menu_type, route_path,
  permission_code, icon, sort_no, status
)
select id, 'GRID_SCOPE_READ', '读取责任网格', 'ACTION', null,
       'grid:read', null, 0, 'ENABLED'
from sys_menu
where menu_code = 'GRID';

insert into sys_menu (
  parent_id, menu_code, menu_name, menu_type, route_path,
  permission_code, icon, sort_no, status
)
select id, 'RESIDENT_SCOPE_READ', '读取范围内居民', 'ACTION', null,
       'resident:read', null, 0, 'ENABLED'
from sys_menu
where menu_code = 'RESIDENT';

insert into sys_role_menu (role_id, menu_id)
select role.id, menu.id
from sys_role role
join sys_menu menu
  on menu.menu_code in ('GRID_SCOPE_READ', 'RESIDENT_SCOPE_READ')
where role.role_code in ('SYSTEM_ADMIN', 'COMMUNITY_STAFF', 'GRID_WORKER');

delete role_menu
from sys_role_menu role_menu
join sys_role role on role.id = role_menu.role_id
join sys_menu menu on menu.id = role_menu.menu_id
where role.role_code = 'GRID_WORKER'
  and menu.menu_code in ('GRID', 'RESIDENT');

update sys_user user
join sys_user_role user_role
  on user_role.user_id = user.id and user_role.status = 'ACTIVE'
join sys_role role
  on role.id = user_role.role_id and role.role_code = 'GRID_WORKER'
set user.security_version = user.security_version + 1
where user.status = 'ENABLED';
