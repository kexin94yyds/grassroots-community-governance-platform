alter table sys_user
  add column password_change_required tinyint(1) not null default 0
  after security_version;

create index idx_sys_user_password_change_required
  on sys_user (password_change_required, status);
