-- Fixed-core access administration and immediate invalidation of stale sessions.

alter table sys_user
  add column security_version bigint not null default 0 after version;

alter table sys_role
  add column version int not null default 0 after status;

alter table sys_menu
  add column version int not null default 0 after status;

alter table sys_user
  add constraint ck_sys_user_security_version check (security_version >= 0);

alter table sys_role
  add constraint ck_sys_role_version check (version >= 0);

alter table sys_menu
  add constraint ck_sys_menu_version check (version >= 0);
