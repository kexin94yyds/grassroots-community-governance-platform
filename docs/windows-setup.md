# Windows 启动与四角色验收

## 1. 预期行为

本项目是一套统一认证系统，不是四套独立网站。四类用户共用登录页和基础布局，但登录后的服务端导航、操作按钮和数据范围必须不同：

| 角色 | 登录后首页 | 侧栏入口 |
|---|---|---|
| 系统管理员 | `/dashboard` | 概览、用户、角色、菜单、事件类别、网格、居民、事件、任务（9） |
| 社区工作人员 | `/dashboard` | 概览、网格、居民、事件、任务（5） |
| 网格员 | `/dashboard` | 概览、事件、任务（3） |
| 居民 | `/resident/home` | 居民服务台（1） |

如果四个账号看到完全相同的侧栏，不属于正常行为，请按第 6 节检查。

## 2. 环境要求

- Windows 10/11
- JDK 17
- Maven 3.9 或更高
- Node.js 22
- MySQL 8.x
- Git

克隆公开源码：

```powershell
git clone https://github.com/kexin94yyds/grassroots-community-governance-platform.git
cd grassroots-community-governance-platform
```

## 3. 数据库与后端

先在 MySQL 中建立独立数据库和最小权限账号。不要把真实密码写入仓库文件。

在 PowerShell 当前窗口设置环境变量，尖括号内容替换为本机实际值：

```powershell
$env:DB_URL = "jdbc:mysql://127.0.0.1:3306/community_governance?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = "<数据库账号>"
$env:DB_PASSWORD = "<数据库密码>"
$env:DATA_ENCRYPTION_KEY = "<随机32字节Base64密钥>"
$env:FRONTEND_ORIGIN = "http://localhost:5173"
$env:BOOTSTRAP_ADMIN_ENABLED = "true"
$env:BOOTSTRAP_ADMIN_USERNAME = "admin"
$env:BOOTSTRAP_ADMIN_PASSWORD = "<至少12位且含大小写、数字、符号的临时密码>"
$env:BOOTSTRAP_ADMIN_REAL_NAME = "系统管理员"
```

启动后端：

```powershell
cd backend
mvn clean spring-boot:run
```

首次启动会由 Flyway 依次执行 V1—V9。成功创建管理员后，应关闭引导开关并重新启动：

```powershell
$env:BOOTSTRAP_ADMIN_ENABLED = "false"
```

## 4. 前端

打开新的 PowerShell 窗口：

```powershell
cd <源码目录>\frontend
npm ci
$env:FRONTEND_PORT = "5173"
$env:DEV_API_TARGET = "http://127.0.0.1:8080"
npm run serve
```

浏览器访问：

```text
http://localhost:5173/login
```

不要混用 `localhost:5173` 与 `127.0.0.1:5173`；后端允许来源应与浏览器实际地址一致。

## 5. 数据库版本验收

在 MySQL 中执行：

```sql
select installed_rank, version, description, success
from flyway_schema_history
order by installed_rank;
```

必须满足：

- V1—V9 全部 `success = 1`。
- 最大版本为 9。
- V9 名称为 `grid worker navigation scope`。

V9 将网格员的 `grid:read`、`resident:read` 拆成底层 `ACTION` 权限，并移除独立“网格管理、居民档案”侧栏入口。网格员仍可在事件和任务页面读取责任范围内的关联信息，但不能维护网格、居民或查看敏感字段。

## 6. 四个身份看起来完全相同时

按顺序检查：

1. 在源码目录执行 `git pull`，确认包含 `V9__grid_worker_navigation_scope.sql`。
2. 停止旧后端进程，再重新启动；只重启前端不会执行 Flyway。
3. 用第 5 节 SQL 确认数据库最大版本为 9。
4. 前端重新执行 `npm ci` 和 `npm run serve`，不要继续使用旧 `dist` 或旧开发进程。
5. 在页面右上角执行“退出登录”，不要只在地址栏改页面。
6. 清除 `localhost:5173` 的站点 Cookie 后重新登录，确保上一角色 Session 和导航缓存已清理。
7. 查看浏览器开发者工具 Network：
   - `/api/auth/me` 的 `roles` 应与当前账号一致；
   - `/api/auth/navigation` 应分别返回 9/5/3/1 个入口；
   - 不能用前端隐藏代替后端接口 403。

## 7. 自动化验收

仓库提供两层四角色验证：

- `scripts/role-navigation-smoke.mjs`：验证服务端角色、导航和 10 组接口状态。
- `scripts/role-navigation-ui-e2e.py`：同一真实浏览器依次登录四个账号，验证中文菜单集合、首页落点、隐藏地址跳无权页、退出后无导航残留，以及控制台/页面/网络错误为 0。

验证脚本必须使用隔离数据库和临时账号；密码只通过环境变量提供，不得写入源码、命令历史、截图或日志。
