-- 居民注册手机号只用于与居民档案的不可逆哈希匹配，不保留在账号申请明文字段。

update sys_user
set phone = null
where account_type = 'RESIDENT'
  and phone is not null;
