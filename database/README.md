# 数据库说明

数据库结构已纳入后端 Flyway 版本管理，当前迁移链为：

- [`V1__baseline.sql`](../backend/src/main/resources/db/migration/V1__baseline.sql)
- [`V2__soft_user_role_lifecycle.sql`](../backend/src/main/resources/db/migration/V2__soft_user_role_lifecycle.sql)
- [`V3__registration_and_resident_portal.sql`](../backend/src/main/resources/db/migration/V3__registration_and_resident_portal.sql)
- [`V4__resident_registration_phone_privacy.sql`](../backend/src/main/resources/db/migration/V4__resident_registration_phone_privacy.sql)
- [`V5__access_control_and_session_freshness.sql`](../backend/src/main/resources/db/migration/V5__access_control_and_session_freshness.sql)
- [`V6__resident_sensitive_access_audit.sql`](../backend/src/main/resources/db/migration/V6__resident_sensitive_access_audit.sql)
- [`V7__governance_attachment_navigation_and_audit.sql`](../backend/src/main/resources/db/migration/V7__governance_attachment_navigation_and_audit.sql)
- [`V8__password_lifecycle.sql`](../backend/src/main/resources/db/migration/V8__password_lifecycle.sql)
- [`V9__grid_worker_navigation_scope.sql`](../backend/src/main/resources/db/migration/V9__grid_worker_navigation_scope.sql)

当前迁移链共有 17 张业务表。V9 将网格员的“网格/居民底层只读权限”与侧栏菜单入口分离，网格员侧栏只保留概览、事件和任务；V8 为用户增加强制改密状态和查询索引，管理员重置后用户必须完成本人改密，旧会话通过安全版本失效。V7 为事件类别补充乐观锁版本，为事件附件补充软删除审计字段、`upload_token` 和 `file_purged_at`，新增带 `task_id`、`upload_token`、`file_purged_at` 和软删除审计字段的 `task_attachment`，为居民敏感访问审计补充历史范围网格，并种入动态导航、附件删除和敏感审计读取所需权限。V6 历史审计没有不可变范围快照时保持 `scope_grid_id = NULL`，V7 不根据居民当前网格回填。附件上传令牌在同一事件或任务内建立唯一约束，用于失败重试幂等；软删提交后清理物理文件，`file_purged_at` 为空的记录由定时任务扫描重试，事件与任务附件元数据及删除审计均不做物理删除。

约定：

1. 已发布的迁移文件不可修改。
2. 后续结构变更新增更高版本迁移，不覆盖 V1 至 V9。
3. 不在迁移脚本中保存真实账号、密码或环境地址。
4. 迁移应先在空库和脱敏备份库验证，再进入正式环境。
