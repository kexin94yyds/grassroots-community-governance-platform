# 前后端 API 契约

> 版本：0.5  
> 日期：2026-08-08  
> 状态：四类角色多账号 API 闭环；动态导航、事件类别、事件/任务附件、敏感访问审计与扩展治理大屏纳入同一契约

## 1. 通用约定

- API 统一前缀为 `/api`。
- JSON 字段使用 `camelCase`，数据库字段使用 `snake_case`。
- `BIGINT` 标识符在 JSON 中使用字符串，前端不得转为 JavaScript `Number`。
- 日期时间使用 ISO-8601 字符串；服务端业务时区固定为 `Asia/Shanghai`。
- 列表页参数从 `page=1` 开始，`size` 由服务端限制最大值。
- 查询使用 `GET`，新增使用 `POST`，资料修改使用 `PUT`，停用或归档使用 `PATCH`。
- 核心业务主记录不提供 `DELETE`；状态迁移只能调用动作接口。附件删除是受权限、数据范围和状态约束的软删除，不会物理抹除元数据或审计痕迹。事务提交后才清理物理文件；清理失败或进程中断时，保留 `file_purged_at` 为空的待清理记录并由后续扫描重试。
- 用户角色替换也不物理删除关联记录，而是结束旧关系后按需重新激活。

成功响应：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {},
  "timestamp": "2026-07-31T16:00:00+08:00"
}
```

分页数据：

```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "size": 20
}
```

错误仍使用正确的 HTTP 状态码：

| HTTP 状态 | 语义 |
|---:|---|
| `400` | 参数或业务前置条件不合法 |
| `401` | 未登录或会话失效 |
| `403` | 操作权限或数据范围不足 |
| `404` | 资源不存在，或当前用户无权感知该资源 |
| `409` | 非法状态跳转、重复数据或乐观锁冲突 |
| `500` | 未预期的服务端错误 |

## 2. 认证与 CSRF

平台采用服务端 Session，不在 `localStorage` 或 `sessionStorage` 保存认证令牌。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/auth/csrf` | 初始化或刷新 CSRF Cookie |
| `POST` | `/api/auth/login` | 用户名、密码登录 |
| `GET` | `/api/auth/me` | 当前用户、角色、权限和数据范围摘要 |
| `GET` | `/api/auth/navigation` | 当前账号获授权且启用的动态 `MENU` 导航项 |
| `POST` | `/api/auth/logout` | 注销当前会话 |

前端 Axios 必须设置：

```text
withCredentials = true
xsrfCookieName = XSRF-TOKEN
xsrfHeaderName = X-XSRF-TOKEN
```

登录前获取 CSRF；登录成功和退出后重新获取，因为 Spring Security 会轮换或清理相应 Cookie。浏览器端 Axios 从 `XSRF-TOKEN` Cookie 取原值；curl 等非浏览器客户端也应复制 Cookie 原值到 Header，而不是直接使用响应体中经过 SPA XOR 掩码的 `data.token`。未登录和无权限场景返回 JSON `401/403`，不重定向到 HTML 登录页。服务端会话保存登录时的用户安全版本；账号状态、用户角色、角色权限或菜单状态变化后，相关账号的安全版本递增，旧会话下一次请求立即失效并返回 JSON `401`。只修改角色名称/描述或菜单名称/图标/排序不会使会话失效。

`GET /api/auth/navigation` 只返回当前账号具有权限、菜单状态为 `ENABLED` 且 `type = "MENU"` 的项。每项固定为 `{ id, code, name, routePath, icon, sortNo }`，按 `sortNo`、`id` 排序。前端以此响应渲染导航，并只在本地已注册路由白名单中解析 `routePath`，不动态注册服务端路由；不得以本地静态菜单补回未授权入口。导航请求成功但列表为空时，登录、根路由和品牌首页均进入 `/forbidden`；只有导航请求失败时，才使用权限过滤后的本地首个已注册页面作为回退。

四类角色导航必须固定区分：`SYSTEM_ADMIN` 返回系统与全局业务菜单，`COMMUNITY_STAFF` 返回概览/网格/居民/事件/任务，`GRID_WORKER` 只返回概览/事件/任务，`RESIDENT` 只返回居民服务台。网格员的 `grid:read`、`resident:read` 由 `ACTION` 权限提供，仅服务于责任范围内的关联数据读取，不产生 `GRID`、`RESIDENT` 导航项。

## 3. 资源接口

| 模块 | 主要接口 |
|---|---|
| 用户 | `GET/POST /api/system/users`、`GET/PUT /api/system/users/{id}`、`PATCH /api/system/users/{id}/status`、`PUT /api/system/users/{id}/roles` |
| 密码生命周期 | `POST /api/auth/password`、`POST /api/system/users/{id}/password-reset` |
| 角色/菜单 | `GET /api/system/roles`、`PUT /api/system/roles/{code}`、`GET /api/system/menus`、`PUT /api/system/menus/{id}` |
| 事件类别 | `GET /api/events/categories`、`GET/POST /api/system/event-categories`、`PUT /api/system/event-categories/{id}` |
| 网格 | `GET/POST /api/grids`、`GET/PUT /api/grids/{id}`、`PATCH /api/grids/{id}/status`、`PUT /api/grids/{id}/assignments`、`GET /api/grids/communities`、`GET /api/grids/worker-options`、`GET /api/grids/community-staff-options` |
| 家庭户 | `GET/POST /api/households`、`GET/PUT /api/households/{id}`、`PATCH /api/households/{id}/status` |
| 居民 | `GET/POST /api/residents`、`GET/PUT /api/residents/{id}`、`PATCH /api/residents/{id}/status` |
| 事件 | `GET/POST /api/events`、`GET /api/events/{id}`、`GET /api/events/{id}/flows` |
| 任务 | `GET/POST /api/tasks`、`GET /api/tasks/{id}`、`GET /api/tasks/{id}/flows` |
| 附件 | `GET/POST /api/events/{eventId}/attachments`、`DELETE /api/events/{eventId}/attachments/{attachmentId}`、`GET /api/files/{attachmentId}`；`GET/POST /api/tasks/{taskId}/attachments`、`GET /api/tasks/{taskId}/attachments/{attachmentId}/content`、`DELETE /api/tasks/{taskId}/attachments/{attachmentId}` |
| 居民附件 | `GET/POST /api/resident-portal/events/{eventId}/attachments`、`GET /api/resident-portal/events/{eventId}/attachments/{attachmentId}/content`、`DELETE /api/resident-portal/events/{eventId}/attachments/{attachmentId}` |
| 大屏 | `GET /api/dashboard/overview`、`GET /api/insights/grids` 等模块洞察接口 |
| 敏感审计 | `GET /api/residents/sensitive-access-logs?action?&fieldType?&keyword?&page=1&size=20` |

前端传入的 `gridId` 或 `communityId` 只能缩小查询范围，不能扩大当前用户的数据权限。
`GET /api/grids` 的 `areaType` 查询参数允许 `GRID` 或 `COMMUNITY`，默认 `GRID`。非全局数据范围查询社区时，仅返回当前可访问网格的父社区。
`GET /api/insights/grids` 的 `communities[].grids[]` 同时返回 `centerLongitude`、`centerLatitude` 和 `geoReady`。只有两个坐标均非空，且经度在 `-180..180`、纬度在 `-90..90` 范围内时 `geoReady` 才为 `true`；前端会执行同样校验，未通过的网格只进入待定位清单。

### 3.1 写接口请求体

下表省略的字段不应由前端额外提交。特别是资料 `PUT` 不接受 `status`、业务编号、归属网格等受控字段；已有记录的修改和状态操作均携带非负整数 `version`。

| 接口 | 请求体 |
|---|---|
| `POST /api/system/users` | `{ username, password, realName, phone?, roleCodes: string[] }` |
| `PUT /api/system/users/{id}` | `{ realName, phone?, version }` |
| `PATCH /api/system/users/{id}/status` | `{ enabled: boolean, version }` |
| `PUT /api/system/users/{id}/roles` | `{ roleCodes: string[], version }` |
| `POST /api/auth/password` | `{ oldPassword, newPassword }`；新旧密码不同，成功后全部旧会话失效 |
| `POST /api/system/users/{id}/password-reset` | `{ temporaryPassword, version }`；响应不回显密码，目标账号下次登录必须改密 |
| `PUT /api/system/roles/{code}` | `{ name, description?, status: "ENABLED"\|"DISABLED", menuIds: string[], version }` |
| `PUT /api/system/menus/{id}` | `{ name, icon?, sortNo, status: "ENABLED"\|"DISABLED", version }` |
| `POST /api/system/event-categories` | `{ code, name, description?, sortNo?, status?: "ENABLED"\|"DISABLED" }`；省略 `status` 时默认 `ENABLED` |
| `PUT /api/system/event-categories/{id}` | `{ name, description?, sortNo, status: "ENABLED"\|"DISABLED", version }` |
| `POST /api/grids` | `{ areaType: "COMMUNITY"\|"GRID", communityId?, areaName, address?, centerLongitude?, centerLatitude?, boundaryGeojson? }` |
| `PUT /api/grids/{id}` | `{ areaName, address?, centerLongitude?, centerLatitude?, boundaryGeojson?, version }` |
| `PATCH /api/grids/{id}/status` | `{ status: "ENABLED"\|"DISABLED", version }` |
| `PUT /api/grids/{id}/assignments` | `{ version, assignments: [{ userId, isPrimary }] }`；必须且只能有一个主负责人 |
| `POST /api/households` | `{ gridId, buildingNo?, unitNo?, roomNo?, address }` |
| `PUT /api/households/{id}` | `{ buildingNo?, unitNo?, roomNo?, address, version }` |
| `PATCH /api/households/{id}/status` | `{ status: "ACTIVE"\|"MOVED"\|"ARCHIVED", version }` |
| `POST /api/residents` | `{ gridId, householdId?, realName, gender?, birthDate?, idCard?, phone?, address, isHouseholder, specialGroupTags, remark? }` |
| `PUT /api/residents/{id}` | `{ householdId?, realName, gender?, birthDate?, idCard?, phone?, address, isHouseholder, specialGroupTags, remark?, version }` |
| `PATCH /api/residents/{id}/status` | `{ status: "ACTIVE"\|"MOVED"\|"DECEASED"\|"ARCHIVED", version }` |
| `POST /api/events` | `{ categoryId, gridId, title, description, reportChannel: "WEB"\|"PHONE"\|"ONSITE"\|"OTHER", severity: "LOW"\|"MEDIUM"\|"HIGH"\|"URGENT", address?, reporterName? }` |
| `POST /api/tasks` | `{ gridId, taskType: "ROUTINE_INSPECTION"\|"OTHER", title, description?, priority, assigneeUserId, dueAt? }` |

`POST /api/events/{eventId}/attachments`、`POST /api/tasks/{taskId}/attachments` 与 `POST /api/resident-portal/events/{eventId}/attachments` 使用 `multipart/form-data`，文件字段名为 `file`，可选字段 `requestToken` 为标准 UUID。为兼容非前端客户端可省略该字段；前端必须为每个 `File` 生成并在失败重试中复用同一令牌。服务端在同一业务对象、上传人与有效令牌的组合下返回已有有效附件，不新增记录；新文件不得复用旧令牌。单文件最大 10 MiB，只接受 JPEG、PNG 和 PDF；一次业务记录在前端最多选择 20 个文件。事件或任务先创建、附件后逐个上传，个别附件失败不会回滚已创建主记录，前端只重试失败文件，避免重复上报。

居民归属网格创建后不可通过通用 `PUT` 偷换；要调整家庭户时，目标家庭户必须有效且属于同一网格。居民更新中的 `idCard`、`phone` 为空时保留原值。独立任务只能派给目标网格当前有效的网格员。

### 3.2 详情与流转记录

- 用户、网格、家庭户、居民、事件和任务详情响应中的所有数据库 ID 均为字符串。
- `GET /api/events/{id}/flows` 与 `GET /api/tasks/{id}/flows` 按 `createdAt`、`id` 正序返回，包含 `action`、`fromStatus`、`toStatus`、字符串形式的关联 ID、操作人及备注。
- 读取详情或流转记录前服务端再次校验数据范围，不以列表页或前端路由可见性作为授权依据。
- 事件附件对象为 `{ id, eventId, originalName, contentType, fileSize, sha256, uploadedBy, uploaderName, createdAt }`；任务附件对象为 `{ id, taskId, originalName, contentType, fileSize, sha256, uploadedBy, uploaderName, createdAt }`；所有 ID 均为字符串。
- 后台事件附件下载继续使用 `GET /api/files/{attachmentId}`，返回二进制响应和文件名，不返回存储路径。任务附件下载仅使用其所属任务的嵌套 `content` 路径，居民附件下载仅使用本人事件的嵌套 `content` 路径。
- 列表、上传、下载和删除均在服务端复核网格数据范围。后台附件删除还校验 `file:delete`、上传人和当前业务状态；居民仅可在本人上报且仍为 `REPORTED` 的事件上操作本人附件，不能通过同网格关系读取或删除其他居民附件。

## 4. 事件与任务动作

动作接口只接受 `POST`，请求体必须包含客户端最后读取到的 `version`。通用 `PUT` 不接收 `status`。

| 动作 | 接口 | 关键字段 |
|---|---|---|
| 受理事件 | `POST /api/events/{id}/accept` | `version`、`remark` |
| 驳回事件 | `POST /api/events/{id}/reject` | `version`、`reason` |
| 派发事件 | `POST /api/events/{id}/assign` | `version`、`assigneeUserId`、`taskTitle?`、`taskDescription?`、`priority`、`dueAt?`、`remark?` |
| 撤销事件 | `POST /api/events/{id}/cancel` | `version`、`reason` |
| 接受任务 | `POST /api/tasks/{id}/accept` | `version` |
| 提交处置 | `POST /api/tasks/{id}/submit-review` | `version`、`handlingResult`、`attachmentIds` |
| 复核任务 | `POST /api/tasks/{id}/review` | `version`、`eventVersion`、`approved`、`remark` |
| 取消任务 | `POST /api/tasks/{id}/cancel` | `version`、`reason` |

任务取消仅适用于尚未接单的独立日常任务；事件派生任务不得单独取消，必须通过事件工作流处理，避免事件与任务状态分裂。
事件派生任务复核必须携带 `eventVersion`；复核退回（`approved=false`）时 `remark` 必填。只有 `submit-review` 可以写入 `handlingResult`，接单和复核请求即使携带该字段也不得覆盖处置结果。
`attachmentIds` 是最多 20 个正整数字符串组成的数组。提交 `POST /api/tasks/{id}/submit-review` 时，服务端逐一确认附件状态有效、`task_id` 等于当前任务且当前执行人对该任务仍有处置权限；事件附件、其他任务附件、跨网格附件或已删除附件均不得借用。空数组仍合法。

事件派生任务的状态联动必须在同一事务中完成：

```text
事件上报：REPORTED
受理：REPORTED → ACCEPTED
派发：ACCEPTED → ASSIGNED；创建 PENDING_ACCEPT 任务
接单：事件 ASSIGNED → PROCESSING；任务 PENDING_ACCEPT → PROCESSING
提交：事件/任务 PROCESSING → PENDING_REVIEW
通过：事件 PENDING_REVIEW → CLOSED；任务 PENDING_REVIEW → COMPLETED
退回：事件/任务 PENDING_REVIEW → PROCESSING
```

首版一个事件最多存在一个未终止任务；由数据库生成列唯一约束和派发 Service 双重校验。所有主表更新必须包含 `id + version` 条件，并同步执行 `version = version + 1`。

### 4.1 治理概览响应

`GET /api/dashboard/overview` 的基础计数沿用 `gridCount`、`residentCount`、`keyPopulationCount`、`pendingEventCount`、`processingEventCount`、`pendingReviewEventCount` 和 `closedEventCount`。其中 `processingEventCount` 必须聚合事件状态 `ACCEPTED`、`ASSIGNED` 与 `PROCESSING`。

响应还必须包含以下数组，均只统计当前账号数据范围内的数据：

```json
{
  "gridEventStats": [
    {
      "gridId": "7",
      "gridCode": "GRD-001",
      "gridName": "第一网格",
      "eventCount": 12,
      "completedWithDeadlineCount": 4,
      "onTimeClosedCount": 3,
      "onTimeCompletionRate": 75.0
    }
  ],
  "categoryStats": [
    {
      "categoryId": "1",
      "categoryName": "环境卫生",
      "eventCount": 6,
      "percentage": 50.0
    }
  ],
  "recentEvents": [
    {
      "id": "9",
      "eventNo": "EVT-20260808-0001",
      "title": "楼道堆物",
      "categoryName": "环境卫生",
      "gridName": "第一网格",
      "status": "PROCESSING",
      "severity": "MEDIUM",
      "reportedAt": "2026-08-08T10:30:00+08:00"
    }
  ]
}
```

`onTimeCompletionRate` 与 `percentage` 均为 `0..100` 的数值百分比，`1` 表示 `1%`，不是 `0.01` 比例小数；没有相应分母时返回 `0`。网格按期办结率的分母仅为有 `due_at` 且已完成的事件派生任务，分子为其中 `completed_at <= due_at` 的任务；未完成的逾期任务和无期限任务不进入该口径。`recentEvents` 按 `reportedAt`、`id` 倒序，最多 10 条。

## 5. 角色、权限与数据范围

数据库角色码固定为：

```text
SYSTEM_ADMIN
COMMUNITY_STAFF
GRID_WORKER
RESIDENT
```

Spring Security 角色 authority 使用 `ROLE_<角色码>`；操作权限不加前缀：

```text
system:user:manage
system:role:manage
system:menu:manage
grid:read
grid:write
grid:assign
resident:read
resident:write
resident:sensitive:read
resident:portal
event:read
event:report
event:accept
event:reject
event:assign
event:cancel
task:read
task:create
task:accept
task:handle
task:review
task:cancel
file:read
file:upload
file:delete
dashboard:read
event:category:manage
resident:sensitive:audit:read
```

角色码、菜单码、路由和权限码是固定核心模型，不提供新增或改码接口。管理员只能维护角色名称、描述、状态和现有菜单关系，以及菜单名称、图标、排序和状态。系统管理员必须保留用户、角色、菜单管理权限，且不能获得任务接单、任务处置或居民服务台权限；居民角色只能保留居民服务台权限；社区工作人员和网格员也不能越过各自固定职责边界。系统管理员角色和用户/角色/菜单管理、居民服务台等核心入口不能停用，仍有启用账号的非管理员角色也不能直接停用。

`SYSTEM_ADMIN` 负责系统管理和全局查看，但不能默认接单或处置任务。服务端统一推导数据范围：

- 系统管理员：全部区域数据。
- 社区工作人员：已分配的一个或多个社区及其子网格；每个社区的有效分配中必须且只能有一名主负责人，只有系统管理员可以维护该关系。
- 网格员：已分配责任网格；处理任务时还必须是任务执行人。
- 居民用户：仅本人绑定居民档案所属网格；只能调用居民服务台并查询本人上报事件。

网格员分配规则与社区一致，但候选人必须为启用的 `GRID_WORKER`；社区候选人必须为启用的 `COMMUNITY_STAFF`。前端权限只用于隐藏无关入口，不能替代后端方法权限和数据范围校验。

`event:category:manage` 仅授予 `SYSTEM_ADMIN`。`file:delete` 仅授予 `SYSTEM_ADMIN`、`COMMUNITY_STAFF` 和 `GRID_WORKER`，但不能替代附件所属业务记录、责任区域、上传人和状态的二次校验；居民不获得该后台权限。`resident:sensitive:audit:read` 仅授予 `SYSTEM_ADMIN` 和 `COMMUNITY_STAFF`，查询结果仍以数据范围过滤。

### 5.1 注册与审核

| 动作 | 方法与路径 | 关键字段 |
|---|---|---|
| 提交注册 | `POST /api/auth/register` | `accountType`、`username`、`password`、`realName`、`phone`；居民另需 `idCardNumber` |
| 审核注册 | `POST /api/system/users/{id}/registration-review` | `decision`、工作人员 `roleCodes`、驳回 `reason`、`version` |
| 居民服务台 | `GET /api/resident-portal/overview` | 本人脱敏档案、事件类别、本人事件 |
| 居民上报 | `POST /api/resident-portal/events` | `categoryId`、`title`、`description`、`severity`、`address` |

公开注册统一创建 `PENDING/DISABLED` 账号。工作人员不能自行选择角色；居民提交的手机号先移除空白与连字符、身份证号转为大写后参与匹配，两个字段都只用于计算 SHA-256 等值指纹并匹配未绑定的既有居民档案，不写入 `sys_user.phone`，接口也不返回具体哪个身份字段不匹配。批准居民申请时，在同一事务中绑定 `resident.user_id`、授予唯一 `RESIDENT` 角色并启用账号。

## 6. 敏感数据与附件

- 响应 DTO 禁止包含 `passwordHash`、`idCardCiphertext`、`idCardHash`、`phoneCiphertext`、`phoneHash`。
- 未获 `resident:sensitive:read` 权限时只返回脱敏展示字段。
- 写入身份证号或手机号前必须配置 `DATA_ENCRYPTION_KEY`，值为随机 32 字节密钥的 Base64；应用使用 AES-256-GCM 加密，并仅用 SHA-256 哈希做等值查重。
- 敏感字段精确检索使用 `POST /api/residents/sensitive-search`，JSON 请求体包含 `type`（`ID_CARD` 或 `PHONE`）、`value`、可选 `gridId`/`status` 以及分页字段；接口只做精确指纹匹配，按当前账号数据范围返回脱敏分页 DTO，并设置 `Cache-Control: no-store`。
- 明文查看使用 `POST /api/residents/{id}/sensitive-view`，JSON 请求体必须提供 5 至 200 字 `purpose`；接口同时校验 `resident:sensitive:read` 和居民数据范围，仅返回独立明文 DTO，并设置 `Cache-Control: no-store`。前端只在弹窗中临时显示 60 秒，关闭弹窗时立即清空。
- `resident_sensitive_access_log` 记录成功的 `SEARCH`/`VIEW` 操作人、居民、字段类型、用途、结果数和时间；不得保存输入明文、密文或等值指纹。
- `GET /api/residents/sensitive-access-logs` 接受可选 `action`（`SEARCH`/`VIEW`）、`fieldType`（`ID_CARD`/`PHONE`/`BOTH`）、`keyword`、`page` 和 `size`，并返回 `Cache-Control: no-store`。它仅返回分页审计元数据 `{ id, operatorUserId, operatorName, operatorUsername, residentId, residentNo, residentName, scopeGridId, scopeGridCode, scopeGridName, action, fieldType, purpose, resultCount, createdAt }`；其中 `residentNo`、`residentName` 已脱敏，接口、响应和日志不得返回敏感输入明文、密文、哈希或指纹。系统管理员查询全局记录；社区工作人员只按日志已写入的 `scopeGridId` 查询所属社区网格，另可查看本人产生的空范围记录，绝不以居民当前 `gridId` 推导历史审计可见性。V6 的空范围历史保持 `NULL`，V7 不以当前居民网格回填它们。
- 附件下载必须经过 `/api/files/{id}` 的 `file:read` 权限和事件网格数据范围校验，不直接暴露上传目录。
- 上传需要 `file:upload` 权限，同时校验声明 MIME、文件签名、10 MiB 大小上限并计算 SHA-256；文件以随机名称写入配置化根目录，数据库只保存元数据。事件与任务附件分别以 `event_id` 或 `task_id` 加 `upload_token` 约束上传重试幂等；软删除记录不再通过原令牌作为有效附件返回。已删除附件在提交后清理物理文件，`file_purged_at` 仅在清理完成后写入；未写入该标记的待清理记录由定时扫描重试，文件已不存在时仍可安全完成标记。

## 7. 实现阶段静态检查

- Controller 不直接注入 Mapper。
- 工作流入口使用事务，非法状态跳转不能退化为通用更新。
- 代码中不得出现 `csrf.disable()`、`SessionCreationPolicy.STATELESS` 或带凭据的通配 CORS。
- Java 状态枚举必须与 Flyway V1 中的 `CHECK` 集合一致；用户角色软失效逻辑必须与 Flyway V2 的生命周期约束一致；注册审核与居民唯一绑定必须与 Flyway V3 约束一致；角色、菜单乐观锁与会话安全版本必须与 Flyway V5 一致；居民敏感访问审计必须与 Flyway V6 一致；类别版本、附件软删除、上传令牌幂等、任务附件、动态菜单权限和审计范围网格必须与 Flyway V7 一致；强制改密状态必须与 Flyway V8 一致；网格员导航与底层范围只读权限分离必须与 Flyway V9 一致。
- 除 `backend/src/main/resources/db/migration` 外不维护第二份建表 SQL。
- 所有详情、状态动作、附件和大屏查询都预留服务端数据范围入口。
