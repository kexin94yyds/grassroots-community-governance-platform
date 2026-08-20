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
- [`V10__role_workbenches.sql`](../backend/src/main/resources/db/migration/V10__role_workbenches.sql)
- [`V11__operation_audit_and_resident_profile.sql`](../backend/src/main/resources/db/migration/V11__operation_audit_and_resident_profile.sql)
- [`V12__opening_report_navigation_scope.sql`](../backend/src/main/resources/db/migration/V12__opening_report_navigation_scope.sql)

当前迁移链共有 24 张业务表。V12 仅将四角色默认菜单收敛到开题报告范围，不删除扩展表或代码；V11 新增 `operation_audit_log`；V10 新增工作台和保留扩展。V1—V12 保持不可变。

事件与任务附件的 `requestToken`/`upload_token` 在所属业务对象内建立唯一约束，同一文件重试返回原记录而不重复写入。V6 遗留的敏感访问审计没有不可变范围快照时继续保持 `scope_grid_id = NULL`，V7 不根据居民当前网格回填历史范围。

约定：

1. 已发布的迁移文件不可修改。
2. 后续结构变更新增更高版本迁移，不覆盖 V1 至 V12。
3. 不在迁移脚本中保存真实账号、密码或环境地址。
4. 迁移应先在空库和脱敏备份库验证，再进入正式环境。
