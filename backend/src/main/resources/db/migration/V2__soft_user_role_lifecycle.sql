-- 用户角色关系采用软失效；替换角色时不物理删除权限关系。

alter table sys_user_role
  add column status varchar(20) not null default 'ACTIVE' after assigned_at,
  add column ended_at datetime(3) after status,
  add key idx_sys_user_role_user_status (user_id, status),
  add constraint ck_sys_user_role_status check (status in ('ACTIVE', 'ENDED')),
  add constraint ck_sys_user_role_lifecycle check (
    (status = 'ACTIVE' and ended_at is null)
    or (status = 'ENDED' and ended_at is not null and ended_at >= assigned_at)
  );
