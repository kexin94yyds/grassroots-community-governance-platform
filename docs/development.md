# 本地开发与首次初始化

> 本文记录本地开发、首次初始化和隔离验收约定。核心 CRUD/动作接口、动态导航、事件类别、事件/任务附件、敏感访问审计和 Vue 表单共用同一冻结契约；正式环境或提交前必须按本文重新执行隔离验证。

## 1. 工具链

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Maven | 3.6.3 或更高 |
| Node.js | 22 |
| MySQL | 8.x |

后端 `pom.xml` 使用 `maven.compiler.release=17`。如果 `mvn -version` 显示的 Java 不是 17，应先调整 `JAVA_HOME`；不要只依据终端中的 `java -version`。

版本选择依据：

- [Spring Boot 4.1 系统要求](https://docs.spring.io/spring-boot/4.1/system-requirements.html)
- [Spring Boot 4.1 Starters](https://docs.spring.io/spring-boot/4.1/reference/using/build-systems.html)
- [MyBatis Spring Boot Starter 兼容矩阵](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)

## 2. 数据库

先建立一个空的 MySQL 8 数据库和最小权限应用账号。服务启动时由 Flyway 按版本执行：

```text
backend/src/main/resources/db/migration/V1__baseline.sql
backend/src/main/resources/db/migration/V2__soft_user_role_lifecycle.sql
backend/src/main/resources/db/migration/V3__registration_and_resident_portal.sql
backend/src/main/resources/db/migration/V4__resident_registration_phone_privacy.sql
backend/src/main/resources/db/migration/V5__access_control_and_session_freshness.sql
backend/src/main/resources/db/migration/V6__resident_sensitive_access_audit.sql
backend/src/main/resources/db/migration/V7__governance_attachment_navigation_and_audit.sql
backend/src/main/resources/db/migration/V8__password_lifecycle.sql
backend/src/main/resources/db/migration/V9__grid_worker_navigation_scope.sql
backend/src/main/resources/db/migration/V10__role_workbenches.sql
backend/src/main/resources/db/migration/V11__operation_audit_and_resident_profile.sql
backend/src/main/resources/db/migration/V12__opening_report_navigation_scope.sql
```

V1 包含 15 张业务表、三类后台角色和首批权限；V2—V10 依次补齐角色生命周期、居民注册、隐私、会话安全、附件、工作台和保留扩展。V11 新增统一操作审计，V12 只将可见导航收敛到开题报告范围，形成 24 张业务表、4 个固定角色和 46 个业务权限码。迁移一旦被正式环境执行，就视为已发布；之后只新增更高版本，不回改 V1 至 V12。

后端读取以下环境变量：

| 变量 | 必填 | 说明 |
|---|---:|---|
| `DB_URL` | 是 | MySQL JDBC 地址 |
| `DB_USERNAME` | 是 | 应用数据库账号 |
| `DB_PASSWORD` | 是 | 应用数据库密码 |
| `SERVER_PORT` | 否 | 默认 `8080` |
| `FRONTEND_ORIGIN` | 否 | 默认 `http://localhost:5173` |
| `SESSION_COOKIE_SECURE` | 生产必填 | HTTPS 环境设为 `true` |
| `DATA_ENCRYPTION_KEY` | 写入居民敏感数据时必填 | 随机 32 字节密钥的 Base64，用于 AES-256-GCM |
| `ATTACHMENT_STORAGE_ROOT` | 否 | 默认 `./data/attachments` |

不要把真实值写入 `application.yml` 或提交到版本库。

## 3. 隔离数据库验证

迁移和冒烟测试只能使用明确命名、可随时丢弃的数据库。下面的库名专用于本项目本次验证；执行删除前先查询并人工确认目标，绝不能把 `DB_URL` 指向已有业务库。

```bash
mysql -u root -p -e "select schema_name from information_schema.schemata where schema_name = 'community_governance_validation_20260731'"
```

确认该名称没有承载需保留的数据后，才能显式重建这一个隔离库：

```bash
mysql -u root -p -e "drop database community_governance_validation_20260731; create database community_governance_validation_20260731 character set utf8mb4 collate utf8mb4_0900_ai_ci"

export DB_URL='jdbc:mysql://localhost:3306/community_governance_validation_20260731?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USERNAME='<仅可访问验证库的 MySQL 账号>'
export DB_PASSWORD='<验证库账号密码>'
export DATA_ENCRYPTION_KEY="$(openssl rand -base64 32)"
```

后端首次启动后，使用只读查询核对 V1 至 V12 均成功且业务表数量符合预期：

```bash
mysql --host=localhost --port=3306 --user="$DB_USERNAME" --password community_governance_validation_20260731
```

然后在 MySQL 客户端执行：

```sql
select installed_rank, version, description, success
from flyway_schema_history
order by installed_rank;

select count(*) as business_table_count
from information_schema.tables
where table_schema = 'community_governance_validation_20260731'
  and table_name <> 'flyway_schema_history';
```

验收记录至少保存：MySQL 版本、Flyway 历史结果、业务表数量、后端启动端口，以及关键 API 的 HTTP 状态和业务状态。不要在记录中保存数据库密码、管理员密码、Session Cookie、CSRF Token 或加密密钥。

隔离验收记录必须反映本次实际运行，不可沿用旧基线数字。完成本轮 `scripts/validation-pipeline.sh` 后记录：

| 项目 | 本轮必须核对的结果 |
|---|---|
| Flyway | V1 至 V12 均 `success=1`，当前版本为 12 |
| 业务表 | `information_schema` 返回 24（不含 `flyway_schema_history`） |
| 种子 | 4 个固定角色、46 个权限码；开题可见导航精确为 11/6/5/4 |
| 后端 | JDK 17 下 104 项测试与可执行 JAR 均成功 |
| API 闭环 | 旧业务闭环、新四角色统计、16 个写权限探针及 P1 生命周期全部 `PASS` |
| 浏览器 | 四角色 26 个开题入口、每角色 2 个核心写交互，以及管理员/居民综合 E2E 全部通过；控制台、页面、站内请求及异常 API 响应为 0 |

V3 验收还应覆盖：工作人员注册保持 `PENDING/DISABLED`、居民身份三字段不匹配时不创建账号、管理员批准后角色与居民档案唯一绑定、居民账号只能访问 `/api/resident-portal/**` 授权数据。
V4 验收还应覆盖：带连字符的居民手机号仍可匹配档案，且待审核居民账号的 `sys_user.phone` 为 `NULL`。附件验收应为本轮配置独立的 `ATTACHMENT_STORAGE_ROOT`，覆盖合法 JPEG/PNG/PDF、声明 MIME 与内容签名不一致被拒绝、授权列表/下载以及下载字节一致性。
V5 验收还应覆盖：固定角色码和菜单编码不可变；系统管理与居民入口等受保护权限不可被错误移除或停用；角色、权限、菜单状态或账号状态变化后，相关账号的旧会话下一次请求立即返回 `401`；纯名称、图标、排序等展示修改不应误踢用户下线。
V6 验收还应覆盖：敏感值使用 POST JSON 传输且不进入 URL；检索结果仍脱敏；查看用途必填；响应禁止缓存；无权限账号和超出数据范围的居民被拒绝；API 与 UI 的 SEARCH/VIEW 均生成不含明文或指纹的审计记录。
V7 验收还应覆盖：`/auth/navigation` 只返回当前账号启用且授权的 `MENU`，服务端成功空导航进入 `/forbidden`，仅请求失败可回退权限过滤后的本地首项；类别创建、更新、陈旧 `version` 冲突和在用类别停用被拒绝；事件/任务/居民附件的上传、列表、下载和删除遵从身份、上传者、状态及数据范围，同一 UUID `requestToken` 重试返回原附件且列表不增长；软删事务提交后清理物理文件，`file_purged_at` 未写入的记录可由定时 pending 扫描重试；居民只能操作本人仍为 `REPORTED` 的事件附件；敏感审计分页严格按历史 `scope_grid_id` 过滤，空范围仅原操作人可见，V6 历史空范围不以居民当前网格回填，响应不含明文、密文、哈希或指纹；概览的处理中、网格按期办结率、类别占比和最近事件与业务表口径一致。

## 4. 首个管理员

首次启动可临时启用环境变量引导：

```text
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=<管理员用户名>
BOOTSTRAP_ADMIN_PASSWORD=<至少 12 位的临时强密码>
BOOTSTRAP_ADMIN_REAL_NAME=<管理员姓名>
```

引导程序只在系统中尚不存在 `SYSTEM_ADMIN` 用户时创建账号，密码写入前使用 BCrypt。创建成功后立即关闭 `BOOTSTRAP_ADMIN_ENABLED`，后续密码修改应走正式用户管理流程。

本人改密使用 `POST /api/auth/password`，需要提交原密码和新密码；成功后全部旧会话失效。管理员可在用户管理中输入一次性临时密码执行重置，响应不会回显密码；被重置账号登录后只能进入强制改密页面，完成修改并重新登录后才能访问普通业务。

## 5. 后端

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean test
mvn -DskipTests package
mvn spring-boot:run
```

后端测试覆盖状态机、请求校验、用户、固定角色与动态导航边界、会话即时失效、注册隐私、社区/网格责任分配、家庭户、敏感数据编解码与规范化、敏感检索/查看/审计、事件类别、事件附件、任务附件、上传令牌幂等、附件归属和任务服务。最终提交前必须重新执行 `clean test` 和 `package`，以当次输出为准；不要以历史测试数量替代本次报告。

认证采用服务端 Session 与 Spring Security 7 SPA CSRF。开发期前端代理让 `/api` 保持同源；生产环境应使用 HTTPS，并由同一站点反向代理前后端。

### 5.1 关键 API 冒烟顺序

后端启动后，先通过 `GET /api/auth/csrf` 获取 Cookie。浏览器端 Axios 会把 `XSRF-TOKEN` Cookie 原值复制到 `X-XSRF-TOKEN` Header；使用 curl 时也必须读取 Cookie 原值，不能直接使用响应体中经过 SPA XOR 掩码的 `data.token`。登录会轮换 Session/CSRF，因此登录后要再次获取 CSRF Cookie。

仓库提供了无第三方依赖的 Node 22 四类角色多账号 API 闭环脚本：

```bash
export SMOKE_BASE_URL='http://127.0.0.1:18080'
export SMOKE_CONFIRM_ISOLATED='YES'
export SMOKE_ADMIN_USERNAME='<已引导的系统管理员>'
export SMOKE_ADMIN_PASSWORD='<管理员密码>'
export SMOKE_WORKER_USERNAME='validation-worker'
export SMOKE_WORKER_PASSWORD='<脚本创建的网格员密码>'
export SMOKE_EVENT_CATEGORY_ID='1' # 仅适用于全新 V1 种子库；其他库先查询类别 ID
/opt/homebrew/opt/node@22/bin/node scripts/runtime-smoke.mjs
```

`SMOKE_CONFIRM_ISOLATED` 必须精确为 `YES`，且账号密码必须显式提供并满足 12—128 位、同时包含大小写字母、数字和特殊字符。脚本默认只允许 `127.0.0.1`、`localhost`、`::1`；远程隔离 CI 必须用 `SMOKE_ALLOWED_REMOTE_HOST` 精确填写目标主机，并额外同时设置 `SMOKE_ALLOW_REMOTE_TARGET=YES` 与 `SMOKE_CONFIRM_REMOTE_DISPOSABLE=YES`。缺少任一护栏时脚本会在第一个 HTTP 请求前失败。curl 版本通过标准输入提交 JSON，密码不会进入 curl 进程参数。

脚本会为每次运行生成唯一用户名，创建独立网格员、社区工作人员、居民和第二个系统管理员。第二个管理员负责派发，引导管理员负责复核，因为服务端禁止任务执行人或派发人复核自己的任务。脚本不会输出密码、Cookie、CSRF Token 或加密密钥；它覆盖动态导航、类别、附件、审计、事件派生任务与独立巡查任务的四类角色多账号闭环，成功时输出本次资源 ID 和最终状态。需要纯 curl/jq 版本时也可使用 `scripts/smoke-api.sh`；该脚本还要求显式设置 `SMOKE_USER_PASSWORD`，不再提供固定默认密码。

手工验证建议按以下顺序执行，便于复用前一步创建的数据：

1. 登录并读取 `/api/auth/me`、`/api/auth/navigation`、固定角色和菜单目录，验证当前账号只收到启用且授权的 `MENU` 及允许的展示配置；根路由只解析服务端导航与本地白名单共同允许的首项，成功空列表进入 `/forbidden`。
2. 创建社区、网格、社区工作人员和网格员用户，分别把社区工作人员分配到社区、网格员分配到网格，并验证必须且只能有一个主负责人。
3. 在该网格创建家庭户和居民，验证详情、编辑、状态动作、敏感字段精确检索与填写用途后的临时查看；关闭弹窗后确认完整号码不再留在 DOM，核对敏感审计分页范围和无明文返回。
4. 创建并更新事件类别，提交陈旧版本确认 `409`，再以仍被非终结事件引用的类别验证不能停用；已终结历史事件不应永久锁死类别。上报事件，上传 JPEG/PNG/PDF 附件并用同一 `requestToken` 重试，验证返回同一附件、列表不增长、授权下载、删除权限和居民本人 `REPORTED` 附件限制。
5. 依次受理、派发、切换为被派发网格员接单；上传任务附件后提交复核，确认跨任务/跨网格附件 ID 被拒绝，最后切回有复核权限的账号完成复核。
6. 另建独立巡查任务，验证接单、提交复核、复核；对尚未接单的独立任务验证取消。创建居民、任务、事件依赖后验证网格停用被拒绝，并核对概览中的处理中聚合、网格按期办结率、类别占比和最近事件。
7. 改变测试账号角色或状态，确认旧会话下一次访问立即得到 `401`；再查询事件与任务流转记录，确认版本递增、状态联动和流记录顺序。

每一步都应同时断言 HTTP 状态、响应 `code`、资源 `version` 和业务状态，不能只以返回 `200` 判定通过。

## 6. 前端

```bash
cd frontend
nvm use
npm ci
npm run lint
npm run build
npm run serve
```

前端默认监听 `5173`，并把 `/api` 代理到 `http://localhost:8080`。导航由服务端列表驱动，但路由仍在本地静态注册并充当白名单；登录、根路由和品牌首页都使用同一首页解析规则。上传 API 对每个 `File` 生成稳定 UUID `requestToken`，失败重试复用该令牌。仓库已包含安装验证生成的 `package-lock.json`，后续使用 `npm ci` 复现依赖树。Element UI 已改为组件与样式按需加载；加入按需注册的上传组件后，最近一次生产构建的 vendor JavaScript 约为 814 KiB，Vue CLI 仍会给出体积提示。

2026-08-01 依赖审计结果：

- 全依赖：3 个低等级、12 个中等级、6 个高等级；主要位于 Vue CLI 5、webpack、ESLint 等旧工具链的传递依赖。
- 已安全替换 `babel-eslint`、升级 Vue 2.7.16 和 ESLint 兼容链。不要执行 `npm audit fix --force` 或用无依据的 `overrides` 强压版本。

Vue 2 是为复用课程旧项目而保留的兼容性选择。彻底消除剩余告警需要单独迁移 Vue 3 + Vite + Element Plus。

## 7. 静态校验

不安装项目依赖也可从项目根目录执行：

```bash
node scripts/validate-scaffold.mjs
```

该脚本检查版本、目录、Flyway 迁移命名与单一事实源、外键顺序、状态枚举、权限种子、Session/CSRF、安全禁用项、导航白名单与首页回退、上传令牌幂等、前端认证客户端、API 冒烟脚本和 Markdown 链接。它不能替代 Maven 测试、前端构建或真实 MySQL 迁移验证。

## 8. 浏览器 E2E 回归

仓库提供[有状态隔离浏览器 E2E 脚本](../scripts/ui-e2e.py)，用于锁定多视图的统计、筛选、移动端密度和可读关联字段。它会执行居民敏感字段查看与精确检索 POST，并在隔离库写入访问审计，因此绝不是只读脚本。脚本使用 PEP 723 内联依赖并由 `uv` 隔离执行，固定使用 Python 3.13、Playwright 1.61.0 和兼容的同步桥接版本，不修改前端 `package.json` 或全局 Python 环境；在 macOS 上默认使用已安装的 Google Chrome，其他环境可通过 `E2E_BROWSER_EXECUTABLE` 指定 Chromium 可执行文件。

先启动后端和前端，并确保被测账号具备网格、居民、事件和任务读取权限。账号密码只能通过环境变量提供，不要写入脚本、命令历史、`.env` 或仓库文件：

```bash
export E2E_BASE_URL='http://localhost:5173'
export E2E_CONFIRM_ISOLATED='YES'
export E2E_USERNAME='<具备本回归所需权限的隔离账号>'
read -rs 'E2E_PASSWORD?E2E password: '
export E2E_PASSWORD
export E2E_RESIDENT_USERNAME='<已审核居民账号>'
read -rs 'E2E_RESIDENT_PASSWORD?Resident E2E password: '
export E2E_RESIDENT_PASSWORD
export E2E_ARTIFACT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/community-governance-ui-e2e.XXXXXX")"
uv run --script scripts/ui-e2e.py
unset E2E_PASSWORD E2E_RESIDENT_PASSWORD E2E_CONFIRM_ISOLATED E2E_ARTIFACT_DIR
```

若系统没有可用 Chrome/Chromium，可先安装 Playwright 管理的 Chromium：

```bash
uv run --with playwright==1.61.0 playwright install chromium
```

脚本必须使用本轮新建或空的绝对产物目录；拒绝项目目录、用户主目录、系统临时根目录、符号链接和非空复用目录。默认只允许 loopback；远程隔离目标必须用 `E2E_ALLOWED_REMOTE_HOST` 精确填写目标主机，并再同时设置 `E2E_ALLOW_REMOTE_TARGET=YES` 与 `E2E_CONFIRM_REMOTE_DISPOSABLE=YES`。浏览器会阻断离开获准源站的导航与请求，跨源重定向不能绕过护栏。失败时不再无条件截取整页现场，避免敏感弹窗恰好打开时落盘明文。

脚本会产生隔离审计记录，并检查：

1. 管理员和居民两类浏览器账号的动态菜单权限、根路由首页解析、社区人员分配入口，以及网格、居民、家庭户、事件、任务和社区拓扑接口中的可读关联字段。
2. 空间地图只为有效真实坐标绘制点位，缺坐标网格进入待定位清单；关键词筛选后不新增或重复点位，并始终隐藏分页、保留 OpenStreetMap 署名。
3. 390px 视口概览默认折叠、可访问地展开，零值分布条宽度严格为 `0%`。
4. 居民卡片和任务看板展示可读网格/人员名称，移动端无整页横向溢出。
5. 根路由跳转、动态导航、真实事件/任务流转、概览统计、任务附件、居民附件和敏感访问审计查询入口均可用；审计界面只显示用途和脱敏元数据。
6. 控制台错误、页面错误、失败请求和异常 API 响应均为 0。

截图只写入显式指定的 `E2E_ARTIFACT_DIR`。敏感查看明文只在浏览器内存和临时 DOM 中断言，立即隐藏并确认 DOM 清除后才允许截图；敏感检索输入也会先清空再截图。测试依赖至少一条网格、居民、家庭户、事件和任务记录；应先在隔离验证库执行 API 冒烟脚本生成数据，再运行 E2E。

空间地图使用 OpenStreetMap 标准瓦片地址作为在线底图，不做离线下载、预取或服务端代理。部署环境需要允许浏览器访问 `https://tile.openstreetmap.org`；底图请求失败时，页面会明确提示网络异常，真实坐标清单和待定位清单仍可使用。

## 9. 隔离数据库自动化流水线

项目提供[平台无关的一键验证入口](../scripts/validation-pipeline.sh)。由于当前目录没有 Git 元数据或既定 CI 提供方，该入口不绑定 GitHub Actions、GitLab CI 或 Jenkins；进入实际版本库后，让对应平台执行同一脚本即可，避免维护两套验证逻辑。

流水线按以下顺序执行：

1. 校验 Node 22、JDK 17、MySQL、Maven、`uv` 等工具链。
2. 执行静态脚手架校验、后端测试与 JAR 打包。
3. 通过 `npm ci` 复现前端依赖，再执行 lint 和生产构建。
4. 管理账号创建固定安全前缀加时间戳/进程号的随机 MySQL 数据库、一次性迁移用户和一次性运行用户；Flyway 只用数据库内建表/改表权限，业务连接只用 `SELECT/INSERT/UPDATE/DELETE`，迁移完成后立即撤权。
5. 执行 Node 22 四类角色多账号 API 业务闭环，用其生成的独立数据启动本轮前端并执行管理员、居民真实浏览器 E2E。
6. 通过公开注册 UI 和管理员审核 UI 分别验证工作人员批准、工作人员驳回、居民批准、居民驳回；覆盖审核前拒绝登录、默认批准、Radio 切换、取消不写入、空原因/空角色前端拦截、防重复提交、批准后正确首页和驳回后居民档案不绑定。
7. 无论成功、断言失败还是收到中断信号，都只停止本轮记录的两个进程，并删除本轮创建且通过名称护栏校验的数据库、两个临时用户和隔离附件目录；日志与经过原始字节及截图 OCR 双重扫描、已证明不含合成敏感明文的截图保留。

本地 MySQL 管理账号具备创建/删除临时数据库权限时，可从项目根目录执行：

```bash
export PIPELINE_DB_ADMIN_USERNAME='root'
printf 'MySQL password: '
IFS= read -rs PIPELINE_DB_ADMIN_PASSWORD
printf '\n'
export PIPELINE_DB_ADMIN_PASSWORD

scripts/validation-pipeline.sh

unset PIPELINE_DB_ADMIN_PASSWORD
```

无密码的本地测试实例可不设置 `PIPELINE_DB_ADMIN_PASSWORD`。脚本不会输出数据库密码、临时管理员密码、网格员密码、Session Cookie、CSRF Token 或数据加密密钥，也不会把这些值写入项目、测试产物或外部进程参数。

常用覆盖项：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PIPELINE_DB_HOST` | `127.0.0.1` | MySQL 地址 |
| `PIPELINE_DB_PORT` | `3306` | MySQL 端口 |
| `PIPELINE_DB_ADMIN_USERNAME` | `root` | 仅用于创建/删除本轮隔离库和临时用户，不传给 Spring Boot |
| `PIPELINE_DB_ADMIN_PASSWORD` | 空 | 只通过进程环境传入 |
| `PIPELINE_ALLOW_REMOTE_DB` | `0` | 非本机地址必须显式设为 `1`，避免误连远端数据库 |
| `PIPELINE_ALLOWED_REMOTE_DB_HOST` | 空 | 非本机地址必须精确填写本轮获准的数据库主机 |
| `PIPELINE_CONFIRM_REMOTE_DISPOSABLE` | 空 | 非本机地址还必须精确设为 `YES`，形成远程双确认 |
| `PIPELINE_KEEP_DATABASE` | `0` | 仅调试失败现场时设为 `1`；保留时流水线必定返回失败，使用后须人工核对名称再删除 |
| `PIPELINE_JAVA_HOME` | 自动发现 | 必须指向 JDK 17 |
| `PIPELINE_NODE_BIN` | 自动发现 | 必须指向 Node 22 可执行文件 |
| `PIPELINE_BROWSER_EXECUTABLE` | 自动发现 | 可显式指定 Chrome/Chromium 可执行文件 |
| `PIPELINE_BACKEND_PORT` | 随机空闲端口 | 需要固定端口时覆盖 |
| `PIPELINE_FRONTEND_PORT` | 随机空闲端口 | 需要固定端口时覆盖 |
| `PIPELINE_ARTIFACT_ROOT` | 系统临时目录 | 每次运行在其下创建唯一产物目录 |

远程数据库默认被拒绝；CI 使用容器服务名（例如 `mysql`）时，必须用 `PIPELINE_ALLOWED_REMOTE_DB_HOST=mysql` 绑定本轮目标，并同时设置 `PIPELINE_ALLOW_REMOTE_DB=1` 和 `PIPELINE_CONFIRM_REMOTE_DISPOSABLE=YES`，确保该服务只承载可丢弃的测试数据。流水线创建数据库时不使用 `IF NOT EXISTS`，清理前再次校验 `community_governance_ci_` 前缀，避免把碰撞或错误目标静默当成成功。验证回执会分别记录 MySQL 客户端和服务端版本；当前项目要求二者均与 MySQL 8.x 合同兼容。

每次运行的控制台末尾会输出 `artifacts=...`。其中包含静态校验、后端构建、前端构建、API 冒烟、后端/前端服务日志，以及浏览器截图；失败时优先查看对应步骤日志。Linux CI 若没有浏览器，需要先安装 Playwright 1.61.0 对应的 Chromium，或通过 `PIPELINE_BROWSER_EXECUTABLE` 指向可用浏览器。

## 10. 论文演示数据与截图

项目提供[论文图片一键生成入口](../scripts/generate-thesis-figures.sh)。它会创建名称带 `community_governance_thesis_` 安全前缀的一次性数据库，在随机本地端口启动本轮前后端，调用 [seed-demo.mjs](../scripts/seed-demo.mjs) 装载 4 个社区、4 个网格、4 位居民、16 个事件和 15 个任务，再调用 [thesis-screenshots.py](../scripts/thesis-screenshots.py) 生成 7 张 1920×1080 桌面图片和 1 张 390×844 移动端图片。

生成器使用随机临时密码与 AES-256-GCM 数据密钥，凭据只存在于本轮进程环境中。成功、失败或中断后均会精确停止本轮前后端，并在名称护栏通过后删除本轮数据库。图片、题注和重拍命令见[论文截图说明](thesis/README.md)。运行前应先完成一次隔离验证流水线，确保后端 JAR、前端依赖及浏览器环境均已准备好。
