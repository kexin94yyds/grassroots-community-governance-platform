-- Governance completion: category administration, safe attachments, navigation and auditable sensitive access.

set names utf8mb4;

alter table event_category
  add column version int not null default 0 after status,
  add constraint ck_event_category_version check (version >= 0);

alter table event_attachment
  add column upload_token varchar(64) after sha256,
  add column status varchar(20) not null default 'ACTIVE' after uploaded_by,
  add column deleted_by bigint after status,
  add column deleted_at datetime(3) after deleted_by,
  add column file_purged_at datetime(3) after deleted_at,
  add column version int not null default 0 after file_purged_at,
  add key idx_event_attachment_status (event_id, status, created_at),
  add key idx_event_attachment_pending_purge (status, file_purged_at, deleted_at, id),
  add constraint uk_event_attachment_upload_token unique (event_id, upload_token),
  add constraint fk_event_attachment_deleter foreign key (deleted_by) references sys_user (id) on delete set null,
  add constraint ck_event_attachment_status check (status in ('ACTIVE', 'DELETED')),
  add constraint ck_event_attachment_lifecycle check (
    (status = 'ACTIVE' and deleted_at is null)
    or (status = 'DELETED' and deleted_at is not null)
  ),
  add constraint ck_event_attachment_file_purge check (
    (status = 'ACTIVE' and file_purged_at is null)
    or status = 'DELETED'
  );

create table task_attachment (
  id bigint primary key auto_increment,
  task_id bigint not null,
  storage_key varchar(500) not null,
  original_name varchar(255) not null,
  content_type varchar(120) not null,
  file_size bigint not null,
  sha256 char(64) not null,
  upload_token varchar(64),
  uploaded_by bigint,
  status varchar(20) not null default 'ACTIVE',
  deleted_by bigint,
  deleted_at datetime(3),
  file_purged_at datetime(3),
  version int not null default 0,
  created_at datetime(3) not null default current_timestamp(3),
  key idx_task_attachment_task_status (task_id, status, created_at),
  key idx_task_attachment_pending_purge (status, file_purged_at, deleted_at, id),
  constraint uk_task_attachment_storage_key unique (storage_key),
  constraint uk_task_attachment_upload_token unique (task_id, upload_token),
  constraint fk_task_attachment_task foreign key (task_id) references work_task (id) on delete restrict,
  constraint fk_task_attachment_uploader foreign key (uploaded_by) references sys_user (id) on delete set null,
  constraint fk_task_attachment_deleter foreign key (deleted_by) references sys_user (id) on delete set null,
  constraint ck_task_attachment_size check (file_size > 0),
  constraint ck_task_attachment_status check (status in ('ACTIVE', 'DELETED')),
  constraint ck_task_attachment_lifecycle check (
    (status = 'ACTIVE' and deleted_at is null)
    or (status = 'DELETED' and deleted_at is not null)
  ),
  constraint ck_task_attachment_file_purge check (
    (status = 'ACTIVE' and file_purged_at is null)
    or status = 'DELETED'
  )
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

alter table resident_sensitive_access_log
  add column scope_grid_id bigint after resident_id,
  add key idx_sensitive_access_scope_grid_time (scope_grid_id, created_at),
  add constraint fk_sensitive_access_scope_grid
    foreign key (scope_grid_id) references grid_area (id) on delete set null;

-- V6 records predate an immutable scope snapshot.  Do not infer their historical
-- authorization boundary from the resident's current grid: residents may have moved
-- since the access occurred.  NULL therefore remains the privacy-safe legacy value;
-- restricted auditors can only see their own NULL-scope rows, while global auditors
-- retain complete access.

insert into sys_menu (
  id, parent_id, menu_code, menu_name, menu_type, route_path,
  permission_code, icon, sort_no, status
)
values
  (230, null, 'EVENT_CATEGORY', '事件类别', 'MENU', '/system/event-categories',
   'event:category:manage', 'el-icon-collection-tag', 23, 'ENABLED'),
  (1403, 500, 'FILE_DELETE', '删除附件', 'ACTION', null,
   'file:delete', null, 8, 'ENABLED'),
  (1103, 400, 'RESIDENT_SENSITIVE_AUDIT_READ', '敏感访问审计', 'ACTION', null,
   'resident:sensitive:audit:read', null, 3, 'ENABLED');

insert into sys_role_menu (role_id, menu_id)
select role.id, menu.id
from sys_role role
join sys_menu menu on menu.permission_code in (
  'event:category:manage', 'file:delete', 'resident:sensitive:audit:read'
)
where role.role_code = 'SYSTEM_ADMIN'
union all
select role.id, menu.id
from sys_role role
join sys_menu menu on menu.permission_code in ('file:delete', 'resident:sensitive:audit:read')
where role.role_code = 'COMMUNITY_STAFF'
union all
select role.id, menu.id
from sys_role role
join sys_menu menu on menu.permission_code = 'file:delete'
where role.role_code = 'GRID_WORKER';
