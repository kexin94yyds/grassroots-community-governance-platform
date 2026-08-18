-- 双轨注册与居民服务台
-- 既有后台账号保持 STAFF / APPROVED；公开注册统一先进入 PENDING。

set names utf8mb4;

alter table sys_user
  add column account_type varchar(20) not null default 'STAFF' after phone,
  add column approval_status varchar(20) not null default 'APPROVED' after account_type,
  add column requested_resident_id bigint after approval_status,
  add column registration_note varchar(500) after requested_resident_id,
  add column reviewed_by bigint after registration_note,
  add column reviewed_at datetime(3) after reviewed_by,
  add column rejection_reason varchar(500) after reviewed_at,
  add key idx_sys_user_approval (approval_status, account_type, created_at),
  add constraint uk_sys_user_requested_resident unique (requested_resident_id),
  add constraint fk_sys_user_requested_resident
    foreign key (requested_resident_id) references resident (id) on delete restrict,
  add constraint fk_sys_user_reviewer
    foreign key (reviewed_by) references sys_user (id) on delete set null,
  add constraint ck_sys_user_account_type
    check (account_type in ('STAFF', 'RESIDENT')),
  add constraint ck_sys_user_approval_status
    check (approval_status in ('PENDING', 'APPROVED', 'REJECTED')),
  add constraint ck_sys_user_registration_shape check (
    (account_type = 'STAFF' and requested_resident_id is null)
    or account_type = 'RESIDENT'
  );

insert into sys_role (role_code, role_name, description, status)
values ('RESIDENT', '居民用户', '仅访问本人档案、本人诉求和居民服务台', 'ENABLED');

insert into sys_menu (
  id, parent_id, menu_code, menu_name, menu_type, route_path,
  permission_code, icon, sort_no, status
)
values (
  700, null, 'RESIDENT_PORTAL', '居民服务台', 'MENU', '/resident/home',
  'resident:portal', 'el-icon-house', 5, 'ENABLED'
);

insert into sys_role_menu (role_id, menu_id)
select role.id, menu.id
from sys_role role
join sys_menu menu on menu.permission_code = 'resident:portal'
where role.role_code = 'RESIDENT';

