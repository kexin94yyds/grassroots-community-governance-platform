-- Four role workbenches: announcements, service applications and patrol plans.
-- V1--V9 are released migrations and must remain immutable.

set names utf8mb4;

create table service_catalog (
  id bigint primary key auto_increment,
  service_code varchar(50) not null,
  service_name varchar(100) not null,
  description varchar(500),
  sort_no int not null default 0,
  status varchar(20) not null default 'ENABLED',
  version int not null default 0,
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  constraint uk_service_catalog_code unique (service_code),
  constraint ck_service_catalog_status check (status in ('ENABLED', 'DISABLED')),
  constraint ck_service_catalog_version check (version >= 0)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table community_announcement (
  id bigint primary key auto_increment,
  announcement_no varchar(50) not null,
  audience_scope varchar(20) not null,
  community_id bigint,
  title varchar(160) not null,
  content text not null,
  pinned tinyint not null default 0,
  status varchar(20) not null default 'DRAFT',
  created_by bigint not null,
  published_by bigint,
  withdrawn_by bigint,
  published_at datetime(3),
  withdrawn_at datetime(3),
  version int not null default 0,
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  key idx_announcement_visible (status, audience_scope, community_id, pinned, published_at),
  key idx_announcement_creator (created_by, status, created_at),
  constraint uk_announcement_no unique (announcement_no),
  constraint fk_announcement_community foreign key (community_id) references grid_area (id) on delete restrict,
  constraint fk_announcement_creator foreign key (created_by) references sys_user (id) on delete restrict,
  constraint fk_announcement_publisher foreign key (published_by) references sys_user (id) on delete set null,
  constraint fk_announcement_withdrawer foreign key (withdrawn_by) references sys_user (id) on delete set null,
  constraint ck_announcement_scope check (audience_scope in ('GLOBAL', 'COMMUNITY')),
  constraint ck_announcement_scope_shape check (
    (audience_scope = 'GLOBAL' and community_id is null)
    or (audience_scope = 'COMMUNITY' and community_id is not null)
  ),
  constraint ck_announcement_pinned check (pinned in (0, 1)),
  constraint ck_announcement_status check (status in ('DRAFT', 'PUBLISHED', 'WITHDRAWN')),
  constraint ck_announcement_version check (version >= 0)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table announcement_flow (
  id bigint primary key auto_increment,
  announcement_id bigint not null,
  action varchar(20) not null,
  from_status varchar(20),
  to_status varchar(20) not null,
  operator_user_id bigint not null,
  remark varchar(1000),
  created_at datetime(3) not null default current_timestamp(3),
  key idx_announcement_flow_time (announcement_id, created_at, id),
  constraint fk_announcement_flow_announcement foreign key (announcement_id) references community_announcement (id) on delete restrict,
  constraint fk_announcement_flow_operator foreign key (operator_user_id) references sys_user (id) on delete restrict,
  constraint ck_announcement_flow_action check (action in ('CREATE', 'UPDATE', 'PUBLISH', 'WITHDRAW')),
  constraint ck_announcement_flow_status check (
    (from_status is null or from_status in ('DRAFT', 'PUBLISHED', 'WITHDRAWN'))
    and to_status in ('DRAFT', 'PUBLISHED', 'WITHDRAWN')
  )
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table service_application (
  id bigint primary key auto_increment,
  application_no varchar(50) not null,
  resident_id bigint not null,
  applicant_user_id bigint not null,
  grid_id bigint not null,
  service_catalog_id bigint not null,
  request_content text not null,
  appointment_at datetime(3),
  request_token varchar(64),
  status varchar(20) not null default 'SUBMITTED',
  handler_user_id bigint,
  result_summary text,
  rating tinyint,
  rating_remark varchar(500),
  accepted_at datetime(3),
  started_at datetime(3),
  completed_at datetime(3),
  rejected_at datetime(3),
  cancelled_at datetime(3),
  rated_at datetime(3),
  version int not null default 0,
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  key idx_service_application_grid_status_time (grid_id, status, created_at),
  key idx_service_application_handler_status (handler_user_id, status),
  key idx_service_application_resident_time (resident_id, created_at),
  constraint uk_service_application_no unique (application_no),
  constraint uk_service_application_request_token unique (applicant_user_id, request_token),
  constraint fk_service_application_resident foreign key (resident_id) references resident (id) on delete restrict,
  constraint fk_service_application_applicant foreign key (applicant_user_id) references sys_user (id) on delete restrict,
  constraint fk_service_application_grid foreign key (grid_id) references grid_area (id) on delete restrict,
  constraint fk_service_application_catalog foreign key (service_catalog_id) references service_catalog (id) on delete restrict,
  constraint fk_service_application_handler foreign key (handler_user_id) references sys_user (id) on delete set null,
  constraint ck_service_application_status check (
    status in ('SUBMITTED', 'ACCEPTED', 'PROCESSING', 'COMPLETED', 'REJECTED', 'CANCELLED')
  ),
  constraint ck_service_application_rating check (rating is null or rating between 1 and 5),
  constraint ck_service_application_version check (version >= 0)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table service_application_flow (
  id bigint primary key auto_increment,
  application_id bigint not null,
  action varchar(20) not null,
  from_status varchar(20),
  to_status varchar(20) not null,
  operator_user_id bigint not null,
  remark varchar(1000),
  created_at datetime(3) not null default current_timestamp(3),
  key idx_service_application_flow_time (application_id, created_at, id),
  constraint fk_service_application_flow_application foreign key (application_id) references service_application (id) on delete restrict,
  constraint fk_service_application_flow_operator foreign key (operator_user_id) references sys_user (id) on delete restrict,
  constraint ck_service_application_flow_action check (
    action in ('APPLY', 'ACCEPT', 'START', 'COMPLETE', 'REJECT', 'CANCEL', 'RATE')
  ),
  constraint ck_service_application_flow_status check (
    (from_status is null or from_status in ('SUBMITTED', 'ACCEPTED', 'PROCESSING', 'COMPLETED', 'REJECTED', 'CANCELLED'))
    and to_status in ('SUBMITTED', 'ACCEPTED', 'PROCESSING', 'COMPLETED', 'REJECTED', 'CANCELLED')
  )
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table patrol_plan (
  id bigint primary key auto_increment,
  plan_no varchar(50) not null,
  grid_id bigint not null,
  title varchar(160) not null,
  inspection_content text not null,
  scheduled_at datetime(3) not null,
  due_at datetime(3),
  assignee_user_id bigint not null,
  status varchar(20) not null default 'ACTIVE',
  created_by bigint not null,
  completed_at datetime(3),
  cancelled_at datetime(3),
  version int not null default 0,
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  key idx_patrol_plan_grid_status_time (grid_id, status, scheduled_at),
  key idx_patrol_plan_assignee_status (assignee_user_id, status, scheduled_at),
  constraint uk_patrol_plan_no unique (plan_no),
  constraint fk_patrol_plan_grid foreign key (grid_id) references grid_area (id) on delete restrict,
  constraint fk_patrol_plan_assignee foreign key (assignee_user_id) references sys_user (id) on delete restrict,
  constraint fk_patrol_plan_creator foreign key (created_by) references sys_user (id) on delete restrict,
  constraint ck_patrol_plan_status check (status in ('ACTIVE', 'COMPLETED', 'CANCELLED')),
  constraint ck_patrol_plan_version check (version >= 0),
  constraint ck_patrol_plan_due check (due_at is null or due_at >= scheduled_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

alter table work_task
  add column patrol_plan_id bigint after source_event_id,
  add key idx_task_patrol_plan_status (patrol_plan_id, status),
  add constraint uk_work_task_patrol_plan unique (patrol_plan_id),
  add constraint fk_task_patrol_plan foreign key (patrol_plan_id) references patrol_plan (id) on delete restrict;

insert into service_catalog (service_code, service_name, description, sort_no, status)
values
  ('ELDERLY_CARE', '养老关怀', '高龄、独居及失能老人关怀服务', 10, 'ENABLED'),
  ('DISABILITY_SUPPORT', '残障帮扶', '残障居民辅助与无障碍支持', 20, 'ENABLED'),
  ('LIVELIHOOD_ASSISTANCE', '民生帮扶', '困难家庭与日常民生协助', 30, 'ENABLED'),
  ('OTHER', '其他服务', '未归类的社区服务申请', 99, 'ENABLED');

insert into sys_menu (menu_code, menu_name, menu_type, route_path, permission_code, icon, sort_no, status)
values
  ('ADMIN_HOME', '管理驾驶舱', 'MENU', '/admin/home', null, 'el-icon-monitor', 1, 'ENABLED'),
  ('COMMUNITY_HOME', '社区治理中心', 'MENU', '/community/home', null, 'el-icon-office-building', 1, 'ENABLED'),
  ('COMMUNITY_TODO', '今日待办', 'MENU', '/community/todo', null, 'el-icon-timer', 2, 'ENABLED'),
  ('COMMUNITY_SERVICE', '服务申请', 'MENU', '/community/service', null, 'el-icon-service', 61, 'ENABLED'),
  ('COMMUNITY_PATROL', '巡查计划', 'MENU', '/community/patrol', null, 'el-icon-guide', 62, 'ENABLED'),
  ('COMMUNITY_REPORT', '社区报表', 'MENU', '/community/report', null, 'el-icon-data-line', 63, 'ENABLED'),
  ('GRID_HOME', '网格执行台', 'MENU', '/grid/home', null, 'el-icon-position', 1, 'ENABLED'),
  ('GRID_PATROL', '我的巡查', 'MENU', '/grid/patrol', null, 'el-icon-guide', 2, 'ENABLED'),
  ('GRID_EVENT_REPORT', '现场上报', 'MENU', '/grid/event-report', null, 'el-icon-warning-outline', 3, 'ENABLED'),
  ('GRID_TASK', '我的任务', 'MENU', '/grid/tasks', null, 'el-icon-s-order', 4, 'ENABLED'),
  ('GRID_MAP', '责任网格', 'MENU', '/grid/map', null, 'el-icon-map-location', 5, 'ENABLED'),
  ('GRID_HISTORY', '工作记录', 'MENU', '/grid/history', null, 'el-icon-notebook-2', 6, 'ENABLED'),
  ('ANNOUNCEMENT', '公告中心', 'MENU', '/announcements', 'announcement:read', 'el-icon-bell', 64, 'ENABLED'),
  ('SERVICE_CATALOG', '服务目录', 'MENU', '/system/service-catalogs', null, 'el-icon-collection', 65, 'ENABLED'),
  ('ADMIN_AUDIT', '管理审计', 'MENU', '/system/operations', 'system:audit:read', 'el-icon-document-checked', 66, 'ENABLED'),
  ('SYSTEM_HEALTH', '系统健康', 'MENU', '/system/health', 'system:health:read', 'el-icon-odometer', 67, 'ENABLED'),
  ('RESIDENT_REPORT', '事项上报', 'MENU', '/resident/report', null, 'el-icon-edit-outline', 6, 'ENABLED'),
  ('RESIDENT_EVENTS', '我的事项', 'MENU', '/resident/events', null, 'el-icon-warning-outline', 7, 'ENABLED'),
  ('RESIDENT_PROFILE', '我的档案', 'MENU', '/resident/profile', null, 'el-icon-user', 8, 'ENABLED'),
  ('RESIDENT_SERVICE', '社区服务', 'MENU', '/resident/service', null, 'el-icon-service', 9, 'ENABLED'),
  ('RESIDENT_RATING', '服务评价', 'MENU', '/resident/ratings', null, 'el-icon-star-on', 10, 'ENABLED');

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'ADMIN_WORKBENCH_ACTION', '读取管理驾驶舱', 'ACTION', 'workbench:admin:read', null, 1, 'ENABLED'
from sys_menu where menu_code = 'ADMIN_HOME';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'COMMUNITY_WORKBENCH_ACTION', '读取社区治理中心', 'ACTION', 'workbench:community:read', null, 1, 'ENABLED'
from sys_menu where menu_code = 'COMMUNITY_HOME';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'GRID_WORKBENCH_ACTION', '读取网格执行台', 'ACTION', 'workbench:grid:read', null, 1, 'ENABLED'
from sys_menu where menu_code = 'GRID_HOME';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'RESIDENT_WORKBENCH_ACTION', '读取居民服务中心', 'ACTION', 'workbench:resident:read', null, 1, 'ENABLED'
from sys_menu where menu_code = 'RESIDENT_PORTAL';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'ANNOUNCEMENT_GLOBAL_WRITE', '发布全局公告', 'ACTION', 'announcement:global:write', null, 1, 'ENABLED'
from sys_menu where menu_code = 'ANNOUNCEMENT';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'ANNOUNCEMENT_COMMUNITY_WRITE', '发布社区公告', 'ACTION', 'announcement:community:write', null, 2, 'ENABLED'
from sys_menu where menu_code = 'ANNOUNCEMENT';

insert into sys_menu (menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
values ('SERVICE_CATALOG_READ', '读取服务目录', 'ACTION', 'service:catalog:read', null, 1, 'ENABLED');

insert into sys_menu (menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
values ('SERVICE_APPLICATION_READ', '读取服务申请', 'ACTION', 'service:application:read', null, 1, 'ENABLED');

insert into sys_menu (menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
values ('PATROL_READ', '读取巡查计划', 'ACTION', 'patrol:read', null, 1, 'ENABLED');

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'SERVICE_CATALOG_MANAGE', '维护服务目录', 'ACTION', 'service:catalog:manage', null, 2, 'ENABLED'
from sys_menu where menu_code = 'SERVICE_CATALOG';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'SERVICE_APPLICATION_HANDLE', '处理服务申请', 'ACTION', 'service:application:handle', null, 1, 'ENABLED'
from sys_menu where menu_code = 'COMMUNITY_SERVICE';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'SERVICE_APPLICATION_APPLY', '提交服务申请', 'ACTION', 'service:application:apply', null, 1, 'ENABLED'
from sys_menu where menu_code = 'RESIDENT_SERVICE';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'SERVICE_APPLICATION_CANCEL', '撤回服务申请', 'ACTION', 'service:application:cancel', null, 2, 'ENABLED'
from sys_menu where menu_code = 'RESIDENT_SERVICE';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'SERVICE_APPLICATION_RATE', '评价服务申请', 'ACTION', 'service:application:rate', null, 3, 'ENABLED'
from sys_menu where menu_code = 'RESIDENT_SERVICE';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'PATROL_PLAN_WRITE', '维护巡查计划', 'ACTION', 'patrol:plan:write', null, 1, 'ENABLED'
from sys_menu where menu_code = 'COMMUNITY_PATROL';

update sys_menu
set permission_code = null
where menu_code in ('EVENT', 'TASK')
  and menu_type = 'MENU';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'EVENT_SCOPE_READ', '读取范围内事件', 'ACTION', 'event:read', null, 0, 'ENABLED'
from sys_menu where menu_code = 'EVENT';

insert into sys_menu (parent_id, menu_code, menu_name, menu_type, permission_code, icon, sort_no, status)
select id, 'TASK_SCOPE_READ', '读取范围内任务', 'ACTION', 'task:read', null, 0, 'ENABLED'
from sys_menu where menu_code = 'TASK';

insert into sys_role_menu (role_id, menu_id)
select role.id, menu.id
from sys_role role
join sys_menu menu on menu.menu_code in ('EVENT_SCOPE_READ', 'TASK_SCOPE_READ')
where role.role_code in ('SYSTEM_ADMIN', 'COMMUNITY_STAFF', 'GRID_WORKER');

delete role_menu
from sys_role_menu role_menu
join sys_role role on role.id = role_menu.role_id
join sys_menu menu on menu.id = role_menu.menu_id
where role.role_code = 'GRID_WORKER'
  and menu.menu_code in ('DASHBOARD', 'EVENT', 'TASK');

insert into sys_role_menu (role_id, menu_id)
select role.id, menu.id
from sys_role role
join sys_menu menu on menu.menu_code in (
  'ADMIN_HOME', 'ADMIN_WORKBENCH_ACTION', 'ANNOUNCEMENT', 'ANNOUNCEMENT_GLOBAL_WRITE',
  'SERVICE_CATALOG', 'SERVICE_CATALOG_MANAGE', 'SERVICE_APPLICATION_READ', 'PATROL_READ',
  'ADMIN_AUDIT', 'SYSTEM_HEALTH'
)
where role.role_code = 'SYSTEM_ADMIN'
union all
select role.id, menu.id
from sys_role role
join sys_menu menu on menu.menu_code in (
  'COMMUNITY_HOME', 'COMMUNITY_WORKBENCH_ACTION', 'COMMUNITY_TODO', 'COMMUNITY_SERVICE',
  'COMMUNITY_PATROL', 'COMMUNITY_REPORT', 'ANNOUNCEMENT', 'ANNOUNCEMENT_COMMUNITY_WRITE',
  'SERVICE_CATALOG_READ', 'SERVICE_APPLICATION_READ', 'SERVICE_APPLICATION_HANDLE',
  'PATROL_READ', 'PATROL_PLAN_WRITE'
)
where role.role_code = 'COMMUNITY_STAFF'
union all
select role.id, menu.id
from sys_role role
join sys_menu menu on menu.menu_code in (
  'GRID_HOME', 'GRID_WORKBENCH_ACTION', 'GRID_PATROL', 'GRID_EVENT_REPORT', 'GRID_TASK',
  'GRID_MAP', 'GRID_HISTORY', 'ANNOUNCEMENT', 'PATROL_READ'
)
where role.role_code = 'GRID_WORKER'
union all
select role.id, menu.id
from sys_role role
join sys_menu menu on menu.menu_code in (
  'RESIDENT_WORKBENCH_ACTION', 'RESIDENT_REPORT', 'RESIDENT_EVENTS',
  'RESIDENT_PROFILE', 'RESIDENT_SERVICE', 'RESIDENT_RATING', 'ANNOUNCEMENT',
  'SERVICE_CATALOG_READ', 'SERVICE_APPLICATION_APPLY', 'SERVICE_APPLICATION_CANCEL',
  'SERVICE_APPLICATION_RATE'
)
where role.role_code = 'RESIDENT';

update sys_user user
join sys_user_role user_role
  on user_role.user_id = user.id and user_role.status = 'ACTIVE'
join sys_role role
  on role.id = user_role.role_id
set user.security_version = user.security_version + 1
where user.status = 'ENABLED'
  and role.role_code in ('SYSTEM_ADMIN', 'COMMUNITY_STAFF', 'GRID_WORKER', 'RESIDENT');
