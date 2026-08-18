-- 基层社区网格化综合治理信息平台
-- MySQL 8.x / Flyway V1 baseline migration
-- 约束：
-- 1. 不执行 DROP，避免误删已有数据。
-- 2. 本文件只在空库执行一次；后续结构变更必须新增 Flyway 版本，禁止回改已发布迁移。
-- 3. 密码只保存 BCrypt 哈希；居民敏感字段由应用层加密后写入。

set names utf8mb4;

create table sys_role (
  id bigint primary key auto_increment,
  role_code varchar(50) not null,
  role_name varchar(80) not null,
  description varchar(255),
  status varchar(20) not null default 'ENABLED',
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  constraint uk_sys_role_code unique (role_code),
  constraint ck_sys_role_status check (status in ('ENABLED', 'DISABLED'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table sys_user (
  id bigint primary key auto_increment,
  username varchar(64) not null,
  password_hash varchar(255) not null,
  real_name varchar(80) not null,
  phone varchar(32),
  status varchar(20) not null default 'ENABLED',
  last_login_at datetime(3),
  version int not null default 0,
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  constraint uk_sys_user_username unique (username),
  constraint ck_sys_user_status check (status in ('ENABLED', 'DISABLED', 'LOCKED'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table sys_user_role (
  user_id bigint not null,
  role_id bigint not null,
  assigned_at datetime(3) not null default current_timestamp(3),
  primary key (user_id, role_id),
  constraint fk_user_role_user foreign key (user_id) references sys_user (id) on delete restrict,
  constraint fk_user_role_role foreign key (role_id) references sys_role (id) on delete restrict
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table sys_menu (
  id bigint primary key auto_increment,
  parent_id bigint,
  menu_code varchar(80) not null,
  menu_name varchar(120) not null,
  menu_type varchar(20) not null default 'MENU',
  route_path varchar(160),
  permission_code varchar(120),
  icon varchar(80),
  sort_no int not null default 0,
  status varchar(20) not null default 'ENABLED',
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  constraint uk_sys_menu_code unique (menu_code),
  constraint uk_sys_menu_permission unique (permission_code),
  constraint fk_sys_menu_parent foreign key (parent_id) references sys_menu (id) on delete restrict,
  constraint ck_sys_menu_type check (menu_type in ('MENU', 'ACTION')),
  constraint ck_sys_menu_status check (status in ('ENABLED', 'DISABLED'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table sys_role_menu (
  role_id bigint not null,
  menu_id bigint not null,
  assigned_at datetime(3) not null default current_timestamp(3),
  primary key (role_id, menu_id),
  constraint fk_role_menu_role foreign key (role_id) references sys_role (id) on delete restrict,
  constraint fk_role_menu_menu foreign key (menu_id) references sys_menu (id) on delete restrict
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table grid_area (
  id bigint primary key auto_increment,
  parent_id bigint,
  area_code varchar(50) not null,
  area_name varchar(120) not null,
  area_type varchar(20) not null,
  address varchar(255),
  center_longitude decimal(10,7),
  center_latitude decimal(10,7),
  boundary_geojson json,
  status varchar(20) not null default 'ENABLED',
  version int not null default 0,
  created_by bigint,
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  key idx_grid_area_parent_status (parent_id, status),
  constraint uk_grid_area_code unique (area_code),
  constraint fk_grid_area_parent foreign key (parent_id) references grid_area (id) on delete restrict,
  constraint fk_grid_area_creator foreign key (created_by) references sys_user (id) on delete set null,
  constraint ck_grid_area_type check (area_type in ('COMMUNITY', 'GRID')),
  constraint ck_grid_area_status check (status in ('ENABLED', 'DISABLED'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table user_area_assignment (
  id bigint primary key auto_increment,
  area_id bigint not null,
  user_id bigint not null,
  assignment_type varchar(30) not null,
  is_primary tinyint not null default 0,
  status varchar(20) not null default 'ACTIVE',
  assigned_at datetime(3) not null default current_timestamp(3),
  ended_at datetime(3),
  assigned_by bigint,
  active_marker tinyint generated always as (
    case when status = 'ACTIVE' then 1 else null end
  ) stored,
  active_primary_marker tinyint generated always as (
    case when status = 'ACTIVE' and is_primary = 1 then 1 else null end
  ) stored,
  key idx_user_area_user_status (user_id, status),
  key idx_user_area_area_status (area_id, status),
  constraint uk_user_area_active unique (area_id, user_id, assignment_type, active_marker),
  constraint uk_user_area_primary unique (area_id, assignment_type, active_primary_marker),
  constraint fk_user_area_area foreign key (area_id) references grid_area (id) on delete restrict,
  constraint fk_user_area_user foreign key (user_id) references sys_user (id) on delete restrict,
  constraint fk_user_area_assigner foreign key (assigned_by) references sys_user (id) on delete set null,
  constraint ck_user_area_type check (assignment_type in ('COMMUNITY_STAFF', 'GRID_WORKER')),
  constraint ck_user_area_primary check (is_primary in (0, 1)),
  constraint ck_user_area_status check (status in ('ACTIVE', 'ENDED')),
  constraint ck_user_area_lifecycle check (
    (status = 'ACTIVE' and ended_at is null) or
    (status = 'ENDED' and ended_at is not null and ended_at >= assigned_at)
  )
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table household (
  id bigint primary key auto_increment,
  household_no varchar(50) not null,
  grid_id bigint not null,
  building_no varchar(50),
  unit_no varchar(50),
  room_no varchar(50),
  address varchar(255) not null,
  status varchar(20) not null default 'ACTIVE',
  version int not null default 0,
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  key idx_household_grid_status (grid_id, status),
  constraint uk_household_no unique (household_no),
  constraint fk_household_grid foreign key (grid_id) references grid_area (id) on delete restrict,
  constraint ck_household_status check (status in ('ACTIVE', 'MOVED', 'ARCHIVED'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table resident (
  id bigint primary key auto_increment,
  resident_no varchar(50) not null,
  grid_id bigint not null,
  household_id bigint,
  user_id bigint,
  real_name varchar(80) not null,
  gender varchar(20),
  birth_date date,
  id_card_ciphertext varbinary(512),
  id_card_hash char(64),
  id_card_last4 char(4),
  phone_ciphertext varbinary(256),
  phone_hash char(64),
  phone_last4 char(4),
  address varchar(255) not null,
  is_householder tinyint not null default 0,
  special_group_tags json,
  status varchar(20) not null default 'ACTIVE',
  remark varchar(500),
  version int not null default 0,
  created_by bigint,
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  key idx_resident_grid_status (grid_id, status),
  key idx_resident_household (household_id),
  key idx_resident_phone_hash (phone_hash),
  key idx_resident_name (real_name),
  constraint uk_resident_no unique (resident_no),
  constraint uk_resident_id_card_hash unique (id_card_hash),
  constraint uk_resident_user unique (user_id),
  constraint fk_resident_grid foreign key (grid_id) references grid_area (id) on delete restrict,
  constraint fk_resident_household foreign key (household_id) references household (id) on delete set null,
  constraint fk_resident_user foreign key (user_id) references sys_user (id) on delete set null,
  constraint fk_resident_creator foreign key (created_by) references sys_user (id) on delete set null,
  constraint ck_resident_householder check (is_householder in (0, 1)),
  constraint ck_resident_gender check (gender is null or gender in ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')),
  constraint ck_resident_status check (status in ('ACTIVE', 'MOVED', 'DECEASED', 'ARCHIVED'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table event_category (
  id bigint primary key auto_increment,
  category_code varchar(50) not null,
  category_name varchar(100) not null,
  description varchar(255),
  sort_no int not null default 0,
  status varchar(20) not null default 'ENABLED',
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  constraint uk_event_category_code unique (category_code),
  constraint ck_event_category_status check (status in ('ENABLED', 'DISABLED'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table governance_event (
  id bigint primary key auto_increment,
  event_no varchar(50) not null,
  category_id bigint not null,
  grid_id bigint not null,
  title varchar(160) not null,
  description text not null,
  report_channel varchar(30) not null,
  severity varchar(20) not null default 'MEDIUM',
  status varchar(30) not null default 'REPORTED',
  address varchar(255),
  longitude decimal(10,7),
  latitude decimal(10,7),
  reporter_user_id bigint,
  reporter_name varchar(80),
  reporter_phone_ciphertext varbinary(256),
  reporter_phone_last4 char(4),
  assigned_to_user_id bigint,
  result_summary text,
  reported_at datetime(3) not null default current_timestamp(3),
  accepted_at datetime(3),
  closed_at datetime(3),
  version int not null default 0,
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  key idx_event_grid_status_time (grid_id, status, reported_at),
  key idx_event_assignee_status (assigned_to_user_id, status),
  key idx_event_category_status (category_id, status),
  key idx_event_status_time (status, reported_at),
  constraint uk_governance_event_no unique (event_no),
  constraint fk_event_category foreign key (category_id) references event_category (id) on delete restrict,
  constraint fk_event_grid foreign key (grid_id) references grid_area (id) on delete restrict,
  constraint fk_event_reporter foreign key (reporter_user_id) references sys_user (id) on delete set null,
  constraint fk_event_assignee foreign key (assigned_to_user_id) references sys_user (id) on delete set null,
  constraint ck_event_channel check (report_channel in ('WEB', 'PHONE', 'ONSITE', 'OTHER')),
  constraint ck_event_severity check (severity in ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
  constraint ck_event_status check (
    status in ('REPORTED', 'ACCEPTED', 'ASSIGNED', 'PROCESSING', 'PENDING_REVIEW', 'CLOSED', 'REJECTED', 'CANCELLED')
  )
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table event_attachment (
  id bigint primary key auto_increment,
  event_id bigint not null,
  storage_key varchar(500) not null,
  original_name varchar(255) not null,
  content_type varchar(120) not null,
  file_size bigint not null,
  sha256 char(64) not null,
  uploaded_by bigint,
  created_at datetime(3) not null default current_timestamp(3),
  key idx_attachment_event (event_id, created_at),
  constraint uk_event_attachment_storage_key unique (storage_key),
  constraint fk_attachment_event foreign key (event_id) references governance_event (id) on delete restrict,
  constraint fk_attachment_uploader foreign key (uploaded_by) references sys_user (id) on delete set null,
  constraint ck_event_attachment_size check (file_size > 0)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table work_task (
  id bigint primary key auto_increment,
  task_no varchar(50) not null,
  source_event_id bigint,
  grid_id bigint not null,
  task_type varchar(30) not null,
  title varchar(160) not null,
  description text,
  priority varchar(20) not null default 'MEDIUM',
  status varchar(30) not null default 'PENDING_ACCEPT',
  dispatcher_user_id bigint not null,
  assignee_user_id bigint not null,
  due_at datetime(3),
  assigned_at datetime(3) not null default current_timestamp(3),
  accepted_at datetime(3),
  submitted_at datetime(3),
  completed_at datetime(3),
  handling_result text,
  review_remark varchar(500),
  version int not null default 0,
  active_event_marker bigint generated always as (
    case
      when source_event_id is not null and status not in ('COMPLETED', 'CANCELLED')
        then source_event_id
      else null
    end
  ) stored,
  created_at datetime(3) not null default current_timestamp(3),
  updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
  key idx_task_event_status (source_event_id, status),
  key idx_task_assignee_status_due (assignee_user_id, status, due_at),
  key idx_task_grid_status (grid_id, status),
  constraint uk_work_task_no unique (task_no),
  constraint uk_work_task_active_event unique (active_event_marker),
  constraint fk_task_event foreign key (source_event_id) references governance_event (id) on delete restrict,
  constraint fk_task_grid foreign key (grid_id) references grid_area (id) on delete restrict,
  constraint fk_task_dispatcher foreign key (dispatcher_user_id) references sys_user (id) on delete restrict,
  constraint fk_task_assignee foreign key (assignee_user_id) references sys_user (id) on delete restrict,
  constraint ck_task_type check (task_type in ('EVENT_HANDLE', 'ROUTINE_INSPECTION', 'OTHER')),
  constraint ck_task_priority check (priority in ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
  constraint ck_task_status check (status in ('PENDING_ACCEPT', 'PROCESSING', 'PENDING_REVIEW', 'COMPLETED', 'CANCELLED'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table task_flow (
  id bigint primary key auto_increment,
  task_id bigint not null,
  action varchar(30) not null,
  from_status varchar(30),
  to_status varchar(30) not null,
  operator_user_id bigint,
  remark varchar(1000),
  created_at datetime(3) not null default current_timestamp(3),
  key idx_task_flow_task_time (task_id, created_at),
  constraint fk_task_flow_task foreign key (task_id) references work_task (id) on delete restrict,
  constraint fk_task_flow_operator foreign key (operator_user_id) references sys_user (id) on delete set null,
  constraint ck_task_flow_action check (
    action in ('ASSIGN', 'ACCEPT', 'SUBMIT_REVIEW', 'APPROVE', 'RETURN', 'CANCEL', 'COMMENT')
  ),
  constraint ck_task_flow_to_status check (
    to_status in ('PENDING_ACCEPT', 'PROCESSING', 'PENDING_REVIEW', 'COMPLETED', 'CANCELLED')
  ),
  constraint ck_task_flow_from_status check (
    from_status is null or
    from_status in ('PENDING_ACCEPT', 'PROCESSING', 'PENDING_REVIEW', 'COMPLETED', 'CANCELLED')
  )
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table event_flow (
  id bigint primary key auto_increment,
  event_id bigint not null,
  task_id bigint,
  action varchar(30) not null,
  from_status varchar(30),
  to_status varchar(30) not null,
  operator_user_id bigint,
  remark varchar(1000),
  created_at datetime(3) not null default current_timestamp(3),
  key idx_event_flow_event_time (event_id, created_at),
  key idx_event_flow_task_time (task_id, created_at),
  constraint fk_event_flow_event foreign key (event_id) references governance_event (id) on delete restrict,
  constraint fk_event_flow_task foreign key (task_id) references work_task (id) on delete restrict,
  constraint fk_event_flow_operator foreign key (operator_user_id) references sys_user (id) on delete set null,
  constraint ck_event_flow_action check (
    action in ('REPORT', 'ACCEPT', 'REJECT', 'ASSIGN', 'START', 'SUBMIT_REVIEW', 'APPROVE', 'RETURN', 'CANCEL', 'COMMENT')
  ),
  constraint ck_event_flow_to_status check (
    to_status in ('REPORTED', 'ACCEPTED', 'ASSIGNED', 'PROCESSING', 'PENDING_REVIEW', 'CLOSED', 'REJECTED', 'CANCELLED')
  ),
  constraint ck_event_flow_from_status check (
    from_status is null or
    from_status in ('REPORTED', 'ACCEPTED', 'ASSIGNED', 'PROCESSING', 'PENDING_REVIEW', 'CLOSED', 'REJECTED', 'CANCELLED')
  )
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

insert into sys_role (role_code, role_name, description, status)
values
  ('SYSTEM_ADMIN', '系统管理员', '用户、角色、菜单和全局数据管理', 'ENABLED'),
  ('COMMUNITY_STAFF', '社区工作人员', '居民管理、事件受理、任务派发和复核', 'ENABLED'),
  ('GRID_WORKER', '网格员', '责任网格巡查、任务接单和事件处置', 'ENABLED');

insert into sys_menu (
  id, parent_id, menu_code, menu_name, menu_type, route_path, permission_code, icon, sort_no, status
)
values
  (100, null, 'DASHBOARD', '治理概览', 'MENU', '/dashboard', 'dashboard:read', 'el-icon-data-analysis', 10, 'ENABLED'),
  (200, null, 'SYSTEM_USER', '用户管理', 'MENU', '/system/users', 'system:user:manage', 'el-icon-user', 20, 'ENABLED'),
  (210, null, 'SYSTEM_ROLE', '角色管理', 'MENU', '/system/roles', 'system:role:manage', 'el-icon-s-custom', 21, 'ENABLED'),
  (220, null, 'SYSTEM_MENU', '菜单权限', 'MENU', '/system/menus', 'system:menu:manage', 'el-icon-menu', 22, 'ENABLED'),
  (300, null, 'GRID', '网格管理', 'MENU', '/grids', 'grid:read', 'el-icon-map-location', 30, 'ENABLED'),
  (400, null, 'RESIDENT', '居民档案', 'MENU', '/residents', 'resident:read', 'el-icon-house', 40, 'ENABLED'),
  (500, null, 'EVENT', '治理事件', 'MENU', '/events', 'event:read', 'el-icon-warning-outline', 50, 'ENABLED'),
  (600, null, 'TASK', '网格任务', 'MENU', '/tasks', 'task:read', 'el-icon-s-order', 60, 'ENABLED');

insert into sys_menu (
  id, parent_id, menu_code, menu_name, menu_type, permission_code, sort_no, status
)
values
  (1001, 300, 'GRID_WRITE', '维护网格', 'ACTION', 'grid:write', 1, 'ENABLED'),
  (1002, 300, 'GRID_ASSIGN', '分配责任区', 'ACTION', 'grid:assign', 2, 'ENABLED'),
  (1101, 400, 'RESIDENT_WRITE', '维护居民', 'ACTION', 'resident:write', 1, 'ENABLED'),
  (1102, 400, 'RESIDENT_SENSITIVE_READ', '查看居民敏感信息', 'ACTION', 'resident:sensitive:read', 2, 'ENABLED'),
  (1201, 500, 'EVENT_REPORT', '上报事件', 'ACTION', 'event:report', 1, 'ENABLED'),
  (1202, 500, 'EVENT_ACCEPT', '受理事件', 'ACTION', 'event:accept', 2, 'ENABLED'),
  (1203, 500, 'EVENT_REJECT', '驳回事件', 'ACTION', 'event:reject', 3, 'ENABLED'),
  (1204, 500, 'EVENT_ASSIGN', '派发事件', 'ACTION', 'event:assign', 4, 'ENABLED'),
  (1205, 500, 'EVENT_CANCEL', '撤销事件', 'ACTION', 'event:cancel', 5, 'ENABLED'),
  (1301, 600, 'TASK_CREATE', '创建任务', 'ACTION', 'task:create', 1, 'ENABLED'),
  (1302, 600, 'TASK_ACCEPT', '接受任务', 'ACTION', 'task:accept', 2, 'ENABLED'),
  (1303, 600, 'TASK_HANDLE', '处置任务', 'ACTION', 'task:handle', 3, 'ENABLED'),
  (1304, 600, 'TASK_REVIEW', '复核任务', 'ACTION', 'task:review', 4, 'ENABLED'),
  (1305, 600, 'TASK_CANCEL', '取消任务', 'ACTION', 'task:cancel', 5, 'ENABLED'),
  (1401, 500, 'FILE_READ', '读取附件', 'ACTION', 'file:read', 6, 'ENABLED'),
  (1402, 500, 'FILE_UPLOAD', '上传附件', 'ACTION', 'file:upload', 7, 'ENABLED');

insert into sys_role_menu (role_id, menu_id)
select r.id, m.id
from sys_role r
join sys_menu m
  on (
    r.role_code = 'SYSTEM_ADMIN'
    and m.permission_code not in ('task:accept', 'task:handle')
  )
  or (
    r.role_code = 'COMMUNITY_STAFF'
    and m.permission_code not like 'system:%'
    and m.permission_code not in ('task:accept', 'task:handle')
  )
  or (
    r.role_code = 'GRID_WORKER'
    and m.permission_code in (
      'dashboard:read',
      'grid:read',
      'resident:read',
      'event:read',
      'event:report',
      'task:read',
      'task:accept',
      'task:handle',
      'file:read',
      'file:upload'
    )
  );

insert into event_category (category_code, category_name, description, sort_no, status)
values
  ('CONFLICT_MEDIATION', '矛盾纠纷', '邻里、家庭及其他需要调解的矛盾纠纷', 10, 'ENABLED'),
  ('ENVIRONMENT', '环境卫生', '垃圾、卫生死角、噪声等问题', 20, 'ENABLED'),
  ('PUBLIC_FACILITY', '公共设施', '路灯、道路、井盖、公共设备等问题', 30, 'ENABLED'),
  ('SAFETY_HAZARD', '安全隐患', '消防、用电、建筑及其他安全隐患', 40, 'ENABLED'),
  ('LIVELIHOOD_REQUEST', '民生诉求', '居民反映的日常服务和民生问题', 50, 'ENABLED'),
  ('OTHER', '其他', '未归入现有分类的治理事项', 99, 'ENABLED');
