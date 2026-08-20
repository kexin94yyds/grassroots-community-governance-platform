import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const errors = [];
const ignoredDirectoryNames = new Set([
  ".codex-tmp",
  ".cunzhi-memory",
  ".git",
  "backups",
  "dist",
  "node_modules",
  "target",
]);

const relative = (file) => path.relative(root, file);
const read = (file) => fs.readFileSync(path.join(root, file), "utf8");

function requireFile(file) {
  const absolute = path.join(root, file);
  if (!fs.existsSync(absolute)) {
    errors.push(`缺少文件：${file}`);
    return "";
  }
  return fs.readFileSync(absolute, "utf8");
}

function walk(directory) {
  const absolute = path.join(root, directory);
  if (!fs.existsSync(absolute)) {
    return [];
  }
  return fs.readdirSync(absolute, { withFileTypes: true }).flatMap((entry) => {
    if (entry.isDirectory() && ignoredDirectoryNames.has(entry.name)) {
      return [];
    }
    const child = path.join(absolute, entry.name);
    return entry.isDirectory() ? walk(relative(child)) : [child];
  });
}

function assert(condition, message) {
  if (!condition) {
    errors.push(message);
  }
}

const ELEMENT_UI_SERVICE_IMPORTS = new Set(["Loading", "Message", "MessageBox"]);
const ELEMENT_UI_DIRECTIVES = new Map([["loading", "Loading.directive"]]);

function elementUiComponentName(tagName) {
  return tagName
    .replace(/^el-/, "")
    .split("-")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join("");
}

function reconcileElementUiRegistration(mainSource, vueSources) {
  const issues = [];
  const importMatch = mainSource.match(/import\s*{([\s\S]*?)}\s*from\s*['"]element-ui['"]/);
  const imported = new Set(
    (importMatch?.[1] || "")
      .split(",")
      .map((name) => name.trim().split(/\s+as\s+/)[0])
      .filter(Boolean)
  );
  const registrationMatch = mainSource.match(/\[([\s\S]*?)]\.forEach\(component\s*=>\s*Vue\.use\(component\)\)/);
  const registered = new Set(
    (registrationMatch?.[1] || "")
      .split(",")
      .map((name) => name.trim())
      .filter((name) => /^[A-Z][A-Za-z0-9]*$/.test(name))
  );
  const usedTags = new Set();
  for (const source of vueSources) {
    for (const match of source.matchAll(/<\s*(el-[a-z0-9-]+)\b/g)) usedTags.add(match[1]);
  }

  for (const tag of [...usedTags].sort()) {
    const component = elementUiComponentName(tag);
    if (!imported.has(component)) issues.push(`${tag} 未从 element-ui 按需导入 ${component}`);
    if (!registered.has(component)) issues.push(`${tag} 未通过 Vue.use 注册 ${component}`);
  }
  for (const component of registered) {
    if (!imported.has(component)) issues.push(`Vue.use 注册了未导入的 Element UI 组件 ${component}`);
  }
  for (const service of ELEMENT_UI_SERVICE_IMPORTS) {
    if (imported.has(service) && registered.has(service)) {
      issues.push(`Element UI 服务或指令 ${service} 不应进入组件注册数组`);
    }
  }
  for (const [directive, registration] of ELEMENT_UI_DIRECTIVES) {
    const used = vueSources.some((source) => new RegExp(`\\bv-${directive}\\b`).test(source));
    if (used && !mainSource.includes(`Vue.use(${registration})`)) {
      issues.push(`v-${directive} 未通过白名单入口 Vue.use(${registration}) 注册`);
    }
  }
  return { issues, imported, registered, usedTags };
}

const elementUiPositiveFixture = reconcileElementUiRegistration(
  "import { Button, Loading, Message } from 'element-ui'; [Button].forEach(component => Vue.use(component)); Vue.use(Loading.directive); Vue.prototype.$message = Message;",
  ['<template><el-button v-loading="busy">确定</el-button></template>']
);
const elementUiNegativeFixture = reconcileElementUiRegistration(
  "import { Button } from 'element-ui'; [Button].forEach(component => Vue.use(component));",
  ["<template><el-radio-group /></template>"]
);
assert(elementUiPositiveFixture.issues.length === 0, "Element UI 静态门禁正例自检失败");
assert(
  elementUiNegativeFixture.issues.some((issue) => issue.includes("el-radio-group")),
  "Element UI 静态门禁反例未识别未注册模板标签"
);

const readme = requireFile("README.md");
const spec = requireFile("docs/functional-spec.md");
const apiContract = requireFile("docs/api-contract.md");
const developmentGuide = requireFile("docs/development.md");
const databaseReadme = requireFile("database/README.md");
const migrationPath = "backend/src/main/resources/db/migration/V1__baseline.sql";
const migration = requireFile(migrationPath);
const registrationMigrationPath = "backend/src/main/resources/db/migration/V3__registration_and_resident_portal.sql";
const registrationMigration = requireFile(registrationMigrationPath);
const privacyMigrationPath = "backend/src/main/resources/db/migration/V4__resident_registration_phone_privacy.sql";
const privacyMigration = requireFile(privacyMigrationPath);
const accessMigrationPath = "backend/src/main/resources/db/migration/V5__access_control_and_session_freshness.sql";
const accessMigration = requireFile(accessMigrationPath);
const sensitiveAuditMigrationPath = "backend/src/main/resources/db/migration/V6__resident_sensitive_access_audit.sql";
const sensitiveAuditMigration = requireFile(sensitiveAuditMigrationPath);
const governanceMigrationPath = "backend/src/main/resources/db/migration/V7__governance_attachment_navigation_and_audit.sql";
const governanceMigration = requireFile(governanceMigrationPath);
const passwordMigrationPath = "backend/src/main/resources/db/migration/V8__password_lifecycle.sql";
const passwordMigration = requireFile(passwordMigrationPath);
const gridWorkerNavigationMigrationPath = "backend/src/main/resources/db/migration/V9__grid_worker_navigation_scope.sql";
const gridWorkerNavigationMigration = requireFile(gridWorkerNavigationMigrationPath);
const roleWorkbenchMigrationPath = "backend/src/main/resources/db/migration/V10__role_workbenches.sql";
const roleWorkbenchMigration = requireFile(roleWorkbenchMigrationPath);
const operationAuditMigrationPath = "backend/src/main/resources/db/migration/V11__operation_audit_and_resident_profile.sql";
const operationAuditMigration = requireFile(operationAuditMigrationPath);
const openingNavigationMigrationPath = "backend/src/main/resources/db/migration/V12__opening_report_navigation_scope.sql";
const openingNavigationMigration = requireFile(openingNavigationMigrationPath);
const migrationChain = `${migration}\n${registrationMigration}\n${privacyMigration}\n${accessMigration}\n${sensitiveAuditMigration}\n${governanceMigration}\n${passwordMigration}\n${gridWorkerNavigationMigration}\n${roleWorkbenchMigration}\n${operationAuditMigration}\n${openingNavigationMigration}`;
const pom = requireFile("backend/pom.xml");
const applicationYaml = requireFile("backend/src/main/resources/application.yml");
const packageText = requireFile("frontend/package.json");
const vueConfig = requireFile("frontend/vue.config.js");
const nvmrc = requireFile("frontend/.nvmrc").trim();
const smokeApi = requireFile("scripts/smoke-api.sh");
const runtimeSmoke = requireFile("scripts/runtime-smoke.mjs");
const p1LifecycleSmoke = requireFile("scripts/p1-lifecycle-smoke.mjs");
const roleNavigationSmoke = requireFile("scripts/role-navigation-smoke.mjs");
const roleNavigationUiE2e = requireFile("scripts/role-navigation-ui-e2e.py");
const uiE2e = requireFile("scripts/ui-e2e.py");
const registrationUiE2e = requireFile("scripts/registration-ui-e2e.py");
const validationPipeline = requireFile("scripts/validation-pipeline.sh");
const seedDemo = requireFile("scripts/seed-demo.mjs");
const thesisScreenshots = requireFile("scripts/thesis-screenshots.py");
const thesisFigures = requireFile("scripts/generate-thesis-figures.sh");
const validationSafetyContract = requireFile("scripts/tests/validation-safety-contract.mjs");
const roleWorkbenchMatrixText = requireFile("scripts/role-workbench-matrix.json");
const frontendMain = requireFile("frontend/src/main.js");
const thesisChapter = requireFile("docs/thesis/chapter-system-implementation-and-testing.md");
requireFile("frontend/.eslintrc.js");
const authService = requireFile("backend/src/main/java/com/cunzhi/governance/auth/service/AuthService.java");
const authController = requireFile("backend/src/main/java/com/cunzhi/governance/auth/controller/AuthController.java");
const navigationItem = requireFile("backend/src/main/java/com/cunzhi/governance/auth/dto/NavigationItem.java");
const userAuthMapper = requireFile("backend/src/main/java/com/cunzhi/governance/auth/mapper/UserAuthMapper.java");
const dashboardMapper = requireFile("backend/src/main/java/com/cunzhi/governance/dashboard/mapper/DashboardMapper.java");
const dashboardService = requireFile("backend/src/main/java/com/cunzhi/governance/dashboard/service/DashboardService.java");
const dashboardOverview = requireFile("backend/src/main/java/com/cunzhi/governance/dashboard/dto/DashboardOverview.java");
const dashboardGridEventStat = requireFile("backend/src/main/java/com/cunzhi/governance/dashboard/dto/DashboardGridEventStat.java");
const dashboardCategoryStat = requireFile("backend/src/main/java/com/cunzhi/governance/dashboard/dto/DashboardCategoryStat.java");
const dashboardRecentEvent = requireFile("backend/src/main/java/com/cunzhi/governance/dashboard/dto/DashboardRecentEvent.java");
const householdMapper = requireFile("backend/src/main/java/com/cunzhi/governance/resident/mapper/HouseholdMapper.java");
const householdSummary = requireFile("backend/src/main/java/com/cunzhi/governance/resident/dto/HouseholdSummary.java");
const systemUserMapper = requireFile("backend/src/main/java/com/cunzhi/governance/system/mapper/SystemUserMapper.java");
const userSummary = requireFile("backend/src/main/java/com/cunzhi/governance/system/dto/UserSummary.java");
const attachmentService = requireFile("backend/src/main/java/com/cunzhi/governance/attachment/service/EventAttachmentService.java");
const attachmentFileStore = requireFile("backend/src/main/java/com/cunzhi/governance/attachment/service/AttachmentFileStore.java");
const attachmentPurgeService = requireFile("backend/src/main/java/com/cunzhi/governance/attachment/service/AttachmentPurgeService.java");
const attachmentPurgeMarkerService = requireFile("backend/src/main/java/com/cunzhi/governance/attachment/service/AttachmentPurgeMarkerService.java");
const attachmentPurgeScheduler = requireFile("backend/src/main/java/com/cunzhi/governance/attachment/service/AttachmentPurgeScheduler.java");
const attachmentController = requireFile("backend/src/main/java/com/cunzhi/governance/attachment/controller/EventAttachmentController.java");
const eventAttachmentMapper = requireFile("backend/src/main/java/com/cunzhi/governance/attachment/mapper/EventAttachmentMapper.java");
const fileController = requireFile("backend/src/main/java/com/cunzhi/governance/attachment/controller/FileController.java");
const frontendFilesApi = requireFile("frontend/src/api/files.js");
const residentPortalApi = requireFile("frontend/src/api/residentPortal.js");
const taskApi = requireFile("frontend/src/api/tasks.js");
const uploadTokenUtils = requireFile("frontend/src/utils/uploadToken.js");
const taskAttachmentController = requireFile("backend/src/main/java/com/cunzhi/governance/task/controller/TaskAttachmentController.java");
const taskAttachmentService = requireFile("backend/src/main/java/com/cunzhi/governance/task/service/TaskAttachmentService.java");
const taskAttachmentMapper = requireFile("backend/src/main/java/com/cunzhi/governance/task/mapper/TaskAttachmentMapper.java");
const eventCategoryController = requireFile("backend/src/main/java/com/cunzhi/governance/event/controller/SystemEventCategoryController.java");
const eventCategoryService = requireFile("backend/src/main/java/com/cunzhi/governance/event/service/EventCategoryService.java");
const residentController = requireFile("backend/src/main/java/com/cunzhi/governance/resident/controller/ResidentController.java");
const sensitiveAccessMapper = requireFile("backend/src/main/java/com/cunzhi/governance/resident/mapper/ResidentSensitiveAccessMapper.java");
const sensitiveAccessView = requireFile("backend/src/main/java/com/cunzhi/governance/resident/dto/ResidentSensitiveAccessLogView.java");
const securityConfig = requireFile("backend/src/main/java/com/cunzhi/governance/config/SecurityConfig.java");
const governanceApplication = requireFile("backend/src/main/java/com/cunzhi/governance/GovernanceApplication.java");
const myBatisConfig = requireFile("backend/src/main/java/com/cunzhi/governance/config/MyBatisConfig.java");
const systemAccessService = requireFile("backend/src/main/java/com/cunzhi/governance/system/service/SystemAccessService.java");
const sessionFreshnessFilter = requireFile("backend/src/main/java/com/cunzhi/governance/auth/security/SessionFreshnessFilter.java");
const frontendRouter = requireFile("frontend/src/router/index.js");
const frontendSessionStore = requireFile("frontend/src/store/modules/session.js");
const appLayout = requireFile("frontend/src/layouts/AppLayout.vue");
const loginView = requireFile("frontend/src/views/auth/LoginView.vue");
const navigationStore = requireFile("frontend/src/store/modules/navigation.js");
const navigationUtils = requireFile("frontend/src/utils/navigation.js");
const eventAttachmentServiceTest = requireFile("backend/src/test/java/com/cunzhi/governance/attachment/service/EventAttachmentServiceTest.java");
const attachmentPurgeServiceTest = requireFile("backend/src/test/java/com/cunzhi/governance/attachment/service/AttachmentPurgeServiceTest.java");
const taskAttachmentServiceTest = requireFile("backend/src/test/java/com/cunzhi/governance/task/service/TaskAttachmentServiceTest.java");
const taskActionRequest = requireFile("backend/src/main/java/com/cunzhi/governance/task/dto/TaskActionRequest.java");

for (const [source, label] of [
  [readme, "README"],
  [spec, "功能规格"],
  [apiContract, "API 契约"],
  [developmentGuide, "开发说明"],
  [databaseReadme, "数据库说明"],
]) {
  assert(source.includes("requestToken") || source.includes("upload_token"), `${label} 未说明附件请求令牌幂等`);
}

let packageJson = {};
try {
  packageJson = JSON.parse(packageText);
} catch (error) {
  errors.push(`frontend/package.json 不是合法 JSON：${error.message}`);
}

let roleWorkbenchMatrix = null;
try {
  roleWorkbenchMatrix = JSON.parse(roleWorkbenchMatrixText);
} catch (error) {
  errors.push(`scripts/role-workbench-matrix.json 不是合法 JSON：${error.message}`);
}

assert(pom.includes("<version>4.1.0</version>"), "后端未固定 Spring Boot 4.1.0");
assert(pom.includes("<java.version>17</java.version>"), "后端未固定 Java 17");
assert(
  pom.includes("<artifactId>mybatis-spring-boot-starter</artifactId>") &&
    pom.includes("<mybatis-spring-boot.version>4.0.0</mybatis-spring-boot.version>"),
  "后端未固定 MyBatis Spring Boot Starter 4.0.0"
);
assert(
  pom.includes("<artifactId>spring-boot-starter-webmvc</artifactId>"),
  "Spring Boot 4 项目应使用 spring-boot-starter-webmvc"
);
assert(
  pom.includes("<artifactId>spring-boot-starter-flyway</artifactId>") &&
    pom.includes("<artifactId>flyway-mysql</artifactId>"),
  "MySQL Flyway 依赖不完整"
);

assert(packageJson.dependencies?.vue === "2.7.16", "前端 Vue 版本必须为 2.7.16");
assert(packageJson.dependencies?.["element-ui"] === "2.15.14", "Element UI 版本必须为 2.15.14");
assert(packageJson.dependencies?.["vue-router"], "前端缺少 Vue Router");
assert(packageJson.dependencies?.vuex, "前端缺少 Vuex");
assert(packageJson.dependencies?.axios, "前端缺少 Axios");
assert(packageJson.devDependencies?.["vue-template-compiler"] === "2.7.16", "Vue 与模板编译器版本不一致");
assert(packageJson.devDependencies?.["@babel/eslint-parser"], "前端缺少 @babel/eslint-parser");
assert(!packageJson.devDependencies?.["babel-eslint"], "前端仍依赖已弃用的 babel-eslint");
assert(packageJson.devDependencies?.["babel-plugin-component"], "Element UI 按需加载插件缺失");
assert(nvmrc === "22", "frontend/.nvmrc 必须固定 Node 22");
assert(/port:\s*Number\(process\.env\.FRONTEND_PORT\s*\|\|\s*5173\)/.test(vueConfig), "前端开发端口未固定为 5173");
assert(smokeApi.startsWith("#!/usr/bin/env bash"), "API 冒烟脚本缺少 Bash shebang");
assert(smokeApi.includes('XSRF-TOKEN'), "API 冒烟脚本未按 Cookie 原值处理 SPA CSRF");
assert(smokeApi.includes('event: $eventStatus') && smokeApi.includes('task: $taskStatus'), "API 冒烟脚本未输出事件/任务终态");
assert(
  smokeApi.includes("SMOKE_CONFIRM_ISOLATED") &&
    smokeApi.includes("SMOKE_ALLOW_REMOTE_TARGET") &&
    smokeApi.includes("SMOKE_CONFIRM_REMOTE_DISPOSABLE") &&
    smokeApi.includes('worker_password="${SMOKE_USER_PASSWORD:-}"'),
  "curl API 冒烟脚本缺少隔离、远程双确认或显式密码护栏"
);
assert(
  (fs.statSync(path.join(root, "scripts/smoke-api.sh")).mode & 0o111) !== 0,
  "API 冒烟脚本缺少可执行权限"
);
assert(runtimeSmoke.startsWith("#!/usr/bin/env node"), "Node API 闭环脚本缺少 Node shebang");
assert(runtimeSmoke.includes("REQUIRED_NODE_MAJOR = 22"), "Node API 闭环脚本未固定 Node 22");
assert(
  runtimeSmoke.includes("SMOKE_CONFIRM_ISOLATED") &&
    runtimeSmoke.includes("SMOKE_ALLOW_REMOTE_TARGET") &&
    runtimeSmoke.includes("SMOKE_CONFIRM_REMOTE_DISPOSABLE"),
  "Node API 闭环脚本缺少隔离或远程双确认护栏"
);
assert(runtimeSmoke.includes("SMOKE PASS"), "Node API 闭环脚本未输出成功终态");
assert(
  p1LifecycleSmoke.startsWith("#!/usr/bin/env node") &&
    p1LifecycleSmoke.includes("SMOKE_CONFIRM_ISOLATED") &&
    p1LifecycleSmoke.includes("P1 LIFECYCLE PASS") &&
    p1LifecycleSmoke.includes("PASSWORD_CHANGE_REQUIRED"),
  "P1 生命周期回归脚本缺少隔离护栏或关键断言"
);
assert(
  roleNavigationSmoke.startsWith("#!/usr/bin/env node") &&
    roleNavigationSmoke.includes("ROLE_SMOKE_CONFIRM_ISOLATED") &&
    roleNavigationSmoke.includes("ROLE NAVIGATION PASS") &&
    roleNavigationSmoke.includes("role-workbench-matrix.json") &&
    roleNavigationSmoke.includes("assertStatsPayload") &&
    roleNavigationSmoke.includes("forbiddenRoles") &&
    roleNavigationSmoke.includes("permissionProbes") &&
    !roleNavigationSmoke.includes("ownerProbe") &&
    !roleNavigationSmoke.includes("writeProbes"),
  "四角色导航回归脚本缺少隔离护栏、角色矩阵、统计或越权断言"
);
assert(
  roleNavigationUiE2e.startsWith("#!/usr/bin/env -S uv run --script") &&
    roleNavigationUiE2e.includes("ROLE_UI_CONFIRM_ISOLATED") &&
    roleNavigationUiE2e.includes("ROLE NAVIGATION UI PASS") &&
    roleNavigationUiE2e.includes("role-workbench-matrix.json") &&
    roleNavigationUiE2e.includes("navigation leaked after logout") &&
    roleNavigationUiE2e.includes("bad_api_responses") &&
    roleNavigationUiE2e.includes("visible_write_check") &&
    roleNavigationUiE2e.includes("writeAffordances") &&
    !roleNavigationUiE2e.includes("browser_write_probe") &&
    !roleNavigationUiE2e.includes("owner write probe"),
  "四角色真实浏览器导航回归缺少隔离、矩阵、写操作或错误清理断言"
);
assert(runtimeSmoke.includes("事件附件上传与内容校验"), "Node API 闭环脚本缺少附件上传验证");
assert(runtimeSmoke.includes("固定核心角色权限配置"), "Node API 闭环脚本缺少角色权限配置验证");
assert(runtimeSmoke.includes("分配社区工作人员主负责人"), "Node API 闭环脚本缺少社区责任区验证");
assert(
  runtimeSmoke.includes("requestToken = randomUUID()") &&
    runtimeSmoke.includes("事件附件相同请求令牌重试幂等") &&
    runtimeSmoke.includes("相同 requestToken 重试不得增加事件附件记录"),
  "Node API 闭环脚本缺少附件请求令牌幂等验证"
);
for (const scenario of [
  "系统管理员动态导航",
  "菜单展示配置驱动动态导航",
  "居民服务台动态导航",
  "看板 D2 受理派发处理中聚合",
  "看板 D3/D4 与数据范围一致性",
  "敏感访问审计分页、用途检索与脱敏",
  "管理员产生无责任网格的身份证敏感检索审计",
  "DELETE 跨域预检",
  "提交复核不能引用其他任务附件",
  "提交复核不能引用已删除任务附件",
  "仍被待受理事件引用的类别不能停用"
]) {
  assert(runtimeSmoke.includes(scenario), `Node API 闭环脚本缺少新能力验证：${scenario}`);
}
assert(uiE2e.startsWith("#!/usr/bin/env -S uv run --script"), "浏览器 E2E 脚本缺少 uv script shebang");
assert(uiE2e.includes('# requires-python = ">=3.13,<3.14"'), "浏览器 E2E 未固定兼容的 Python 3.13");
assert(uiE2e.includes('"greenlet==3.2.4"'), "浏览器 E2E 未固定同步桥接兼容版本");
assert(uiE2e.includes("checking fixed-core access administration"), "浏览器 E2E 缺少固定核心权限页面验证");
assert(uiE2e.includes('"playwright==1.61.0"'), "浏览器 E2E 未固定 Playwright 版本");
assert(uiE2e.includes('required_env("E2E_USERNAME")'), "浏览器 E2E 未从环境变量读取用户名");
assert(uiE2e.includes('required_env("E2E_PASSWORD")'), "浏览器 E2E 未从环境变量读取密码");
assert(uiE2e.includes('required_env("E2E_RESIDENT_USERNAME")'), "浏览器 E2E 未从环境变量读取居民用户名");
assert(uiE2e.includes('required_env("E2E_RESIDENT_PASSWORD")'), "浏览器 E2E 未从环境变量读取居民密码");
assert(
  uiE2e.includes('required_env("E2E_CONFIRM_ISOLATED")') &&
    uiE2e.includes("E2E_ALLOW_REMOTE_TARGET") &&
    uiE2e.includes("E2E_CONFIRM_REMOTE_DISPOSABLE") &&
    uiE2e.includes('required_env("E2E_ARTIFACT_DIR")'),
  "浏览器 E2E 缺少隔离、远程双确认或专用产物目录护栏"
);
assert(!uiE2e.includes("SmokeUser-2026"), "浏览器 E2E 不得硬编码验证密码");
assert(!uiE2e.includes("resident-sensitive-view.png"), "浏览器 E2E 不得截图完整居民敏感字段");
assert(!uiE2e.includes('artifact_dir / "failure.png"'), "浏览器 E2E 失败现场不得无条件截图敏感页面");
for (const scenario of [
  "/grids?view=map",
  "/residents?view=card",
  "/events?view=card",
  "/tasks?view=board",
  "zeroWidths",
  "badApiResponses",
  "event-attachments.png",
  "task-attachments.png",
  "resident-attachments.png",
  "resident-sensitive-audit.png",
  "event-flow-history.png",
  "dashboard-d3-d4.png",
  "run_resident_suite",
  "sensitive-access-logs",
  'sidebar_labels == [item["name"] for item in admin_navigation]',
]) {
  assert(uiE2e.includes(scenario), `浏览器 E2E 缺少场景：${scenario}`);
}
assert(
  (fs.statSync(path.join(root, "scripts/ui-e2e.py")).mode & 0o111) !== 0,
  "浏览器 E2E 脚本缺少可执行权限"
);
assert(validationPipeline.startsWith("#!/usr/bin/env bash"), "隔离验证流水线缺少 Bash shebang");
assert(validationPipeline.includes("set -Eeuo pipefail"), "隔离验证流水线未启用严格错误处理");
assert(
  validationPipeline.includes("community_governance_ci_") &&
    validationPipeline.includes("CREATE DATABASE") &&
    validationPipeline.includes("DROP DATABASE"),
  "隔离验证流水线缺少独立数据库创建/清理闭环"
);
assert(
  validationPipeline.includes("PIPELINE_ALLOW_REMOTE_DB") &&
    validationPipeline.includes("PIPELINE_KEEP_DATABASE") &&
    validationPipeline.includes("安全校验拒绝删除"),
  "隔离验证流水线缺少远程数据库与清理安全护栏"
);
assert(
  validationPipeline.includes('frontend/node_modules/.cache') &&
    validationPipeline.includes('find "${frontend_cache_dir}" -depth -delete'),
  "隔离验证流水线缺少中断后前端缓存恢复"
);
assert(
  validationPipeline.includes("runtime-smoke.mjs") &&
    validationPipeline.includes("ui-e2e.py") &&
    validationPipeline.includes("role-navigation-smoke.mjs") &&
    validationPipeline.includes("role-navigation-ui-e2e.py") &&
    validationPipeline.includes("p1-lifecycle-smoke.mjs") &&
    validationPipeline.includes("PIPELINE PASS"),
  "隔离验证流水线未串联 API、角色 API/UI、P1 浏览器与成功终态"
);
assert(
  validationPipeline.includes('wait_for_service "前端" "http://127.0.0.1:${frontend_port}/"'),
  "隔离验证流水线前端就绪探针必须请求可直接访问的根页面"
);
assert(!validationPipeline.includes("SmokeUser-2026"), "隔离验证流水线不得硬编码验证密码");
assert(validationPipeline.includes("DATA_ENCRYPTION_KEY"), "隔离验证流水线缺少临时居民数据加密密钥");
assert(
  validationPipeline.includes("PIPELINE_CONFIRM_REMOTE_DISPOSABLE") &&
    validationPipeline.includes("CREATE USER '${migration_username}'@'%'") &&
    validationPipeline.includes("CREATE USER '${runtime_username}'@'%'") &&
    validationPipeline.includes('SPRING_FLYWAY_USER="${migration_username}"') &&
    validationPipeline.includes('DB_USERNAME="${runtime_username}"') &&
    validationPipeline.includes("unset PIPELINE_DB_ADMIN_USERNAME PIPELINE_DB_ADMIN_PASSWORD") &&
    !validationPipeline.includes('DB_USERNAME="${db_admin_username}"'),
  "隔离验证流水线未把管理员、迁移和运行时数据库权限分离"
);
assert(
  validationPipeline.includes("DROP USER IF EXISTS") &&
    validationPipeline.includes("REVOKE ALL PRIVILEGES, GRANT OPTION") &&
    validationPipeline.includes("SHOW DATABASES LIKE") &&
    validationPipeline.includes("mysql.user WHERE user") &&
    validationPipeline.includes("process still alive") &&
    validationPipeline.includes("sensitive_artifact_matches=0") &&
    validationPipeline.includes('["tesseract", str(path), "stdout", "--psm", "6"]'),
  "隔离验证流水线缺少临时用户清理、迁移撤权或敏感产物扫描"
);
assert(
    validationPipeline.includes("ATTACHMENT_STORAGE_ROOT") &&
    validationPipeline.includes('find "${attachment_dir}" -depth -delete') &&
    validationPipeline.includes("删除后复核失败"),
  "隔离验证流水线缺少隔离附件目录或退出清理"
);
assert(
  !validationPipeline.includes('--execute="CREATE USER') &&
    smokeApi.includes("--data-binary @-") &&
    !smokeApi.includes('--data "${payload}"'),
  "验证脚本仍可能把数据库或 HTTP 密码放入外部进程参数"
);
assert(validationPipeline.includes("SMOKE_CORS_ORIGIN"), "隔离验证流水线未向运行时验证传递 CORS 源站");
assert(
  (fs.statSync(path.join(root, "scripts/validation-pipeline.sh")).mode & 0o111) !== 0,
  "隔离验证流水线缺少可执行权限"
);
assert(seedDemo.startsWith("#!/usr/bin/env node"), "演示数据脚本缺少 Node shebang");
assert(seedDemo.includes("REQUIRED_NODE_MAJOR = 22"), "演示数据脚本未固定 Node 22");
assert(seedDemo.includes("DEMO_CONFIRM_ISOLATED") && seedDemo.includes("must be exactly YES"), "演示数据脚本缺少隔离环境确认护栏");
assert(
  seedDemo.includes("SMOKE_CONFIRM_ISOLATED: smokeConfirmation") &&
    thesisFigures.includes("SMOKE_CONFIRM_ISOLATED=YES"),
  "论文演示数据链未显式传递独立的冒烟隔离确认"
);
for (const profile of ["xingfu", "heyuan", "qinghe", "donghu"]) {
  assert(seedDemo.includes(`code: '${profile}'`), `演示数据脚本缺少社区画像：${profile}`);
}
assert(seedDemo.includes("randomBytes(30)"), "演示数据脚本未随机生成网格员密码");
assert(!seedDemo.includes("SmokeUser-2026"), "演示数据脚本不得硬编码验证密码");
assert(validationSafetyContract.startsWith("#!/usr/bin/env node"), "验证安全合同测试缺少 Node shebang");
assert(
  validationSafetyContract.includes("http_requests=0") &&
    validationSafetyContract.includes("least-privilege contracts"),
  "验证安全合同测试未覆盖发请求前拒绝和最小权限静态合同"
);
assert(!readme.includes("只读浏览器 E2E"), "README 仍把有状态浏览器 E2E 误称为只读");
assert(!developmentGuide.includes("脚本只执行登录和 GET 查询"), "开发文档仍把有状态浏览器 E2E 误称为只读");
assert(
  registrationUiE2e.startsWith("#!/usr/bin/env -S uv run --script") &&
    registrationUiE2e.includes('required_env("E2E_CONFIRM_ISOLATED")') &&
    registrationUiE2e.includes("REGISTRATION_E2E_RESULT") &&
    registrationUiE2e.includes("register_via_ui") &&
    registrationUiE2e.includes("review_account"),
  "注册审核浏览器合同缺少隔离确认、公开注册 UI、管理员审核 UI 或结果回执"
);
assert(
  (fs.statSync(path.join(root, "scripts/seed-demo.mjs")).mode & 0o111) !== 0,
  "演示数据脚本缺少可执行权限"
);
assert(thesisScreenshots.startsWith("#!/usr/bin/env -S uv run --script"), "论文截图脚本缺少 uv script shebang");
assert(thesisScreenshots.includes('# requires-python = ">=3.13,<3.14"'), "论文截图脚本未固定 Python 3.13");
assert(thesisScreenshots.includes('"playwright==1.61.0"'), "论文截图脚本未固定 Playwright 版本");
assert(thesisScreenshots.includes('required_env("THESIS_USERNAME")'), "论文截图脚本未从环境变量读取用户名");
assert(thesisScreenshots.includes('required_env("THESIS_PASSWORD")'), "论文截图脚本未从环境变量读取密码");
for (const filename of [
  "01-login.png",
  "02-dashboard.png",
  "03-user-permissions.png",
  "04-grid-responsibility-map.png",
  "05-resident-cards.png",
  "06-event-flow.png",
  "07-task-board.png",
  "08-mobile-events.png",
]) {
  assert(thesisScreenshots.includes(filename), `论文截图脚本缺少图片：${filename}`);
}
assert(
  (fs.statSync(path.join(root, "scripts/thesis-screenshots.py")).mode & 0o111) !== 0,
  "论文截图脚本缺少可执行权限"
);
assert(thesisFigures.startsWith("#!/usr/bin/env bash") && thesisFigures.includes("set -Eeuo pipefail"), "论文图片入口未启用严格 Bash 模式");
assert(
  thesisFigures.includes("community_governance_thesis_") &&
    thesisFigures.includes("CREATE DATABASE") &&
    thesisFigures.includes("DROP DATABASE"),
  "论文图片入口缺少隔离数据库创建/清理闭环"
);
assert(
  thesisFigures.includes("seed-demo.mjs") &&
    thesisFigures.includes("thesis-screenshots.py") &&
    thesisFigures.includes("THESIS FIGURES PASS"),
  "论文图片入口未串联数据装载、截图与成功终态"
);
assert(!thesisFigures.includes("SmokeUser-2026"), "论文图片入口不得硬编码验证密码");
assert(
  (fs.statSync(path.join(root, "scripts/generate-thesis-figures.sh")).mode & 0o111) !== 0,
  "论文图片入口缺少可执行权限"
);
for (const figureNumber of ["5-1", "5-2", "5-3", "5-4", "5-5", "5-6", "5-7", "5-8"]) {
  assert(thesisChapter.includes(`图${figureNumber}`), `论文实现章节缺少图片编号：图${figureNumber}`);
}
for (const testFact of ["四类固定角色", "动态导航", "任务附件", "敏感访问审计", "0..100"]) {
  assert(thesisChapter.includes(testFact), `论文测试章节缺少本轮能力说明：${testFact}`);
}
for (const action of [
  "事件上报",
  "事件受理",
  "事件派单并派生任务",
  "网格员提交事件任务复核",
  "引导管理员复核事件任务",
  "派发员创建独立任务",
  "网格员提交独立任务复核",
  "引导管理员复核独立任务",
]) {
  assert(runtimeSmoke.includes(action), `Node API 闭环脚本缺少动作：${action}`);
}

const tableMatches = [...migration.matchAll(/create\s+table\s+([a-z0-9_]+)/gi)];
const tables = tableMatches.map((match) => match[1]);
assert(tables.length === 15, `Flyway V1 应包含 15 张表，实际为 ${tables.length}`);
const effectiveBusinessTables = [...tables, "resident_sensitive_access_log", "task_attachment"];
assert(effectiveBusinessTables.length === 17, `迁移链应形成 17 张业务表，实际为 ${effectiveBusinessTables.length}`);
const migrationBusinessTables = [...new Set(
  [...migrationChain.matchAll(/create\s+table\s+([a-z0-9_]+)/gi)].map((match) => match[1])
)];
assert(migrationBusinessTables.length === 24, `V1—V12 迁移链应形成 24 张业务表，实际为 ${migrationBusinessTables.length}`);
assert(new Set(tables).size === tables.length, "Flyway V1 存在重复表名");
assert(!/create\s+table\s+if\s+not\s+exists/i.test(migration), "Flyway V1 不应静默跳过已存在的表");
assert(!/on\s+duplicate\s+key/i.test(migration), "Flyway V1 种子不应静默覆盖冲突");
assert(!/^\s*(drop|truncate|delete)\b/im.test(migration.replace(/^\s*--.*$/gm, "")), "Flyway V1 含破坏性语句");
assert(!/\b(?:tinyint|int|bigint)\s*\(\d+\)/i.test(migration), "Flyway V1 不应使用 MySQL 已弃用的整数显示宽度");
const householdDefinition = migration.match(/create table household \(([\s\S]*?)\n\) engine=/i)?.[1] || "";
assert(/\bversion int not null default 0\b/i.test(householdDefinition), "household 缺少乐观锁 version");
assert(
  /update\s+sys_user\s+set\s+phone\s*=\s*null[\s\S]*account_type\s*=\s*'RESIDENT'/i.test(privacyMigration),
  "Flyway V4 未清除居民注册申请的历史明文手机号"
);
assert(
  attachmentService.includes("AttachmentFileStore") &&
    attachmentFileStore.includes("detectContentType") && attachmentFileStore.includes("SHA-256"),
  "附件服务缺少内容类型或散列校验"
);
assert(attachmentController.includes("/api/events") || attachmentController.includes("/events"), "附件事件接口缺失");
assert(fileController.includes("@PreAuthorize") && fileController.includes("ContentDisposition.attachment"), "附件下载缺少鉴权或下载响应");
assert(frontendFilesApi.includes("listEventAttachments") && frontendFilesApi.includes("downloadAuthorizedFile"), "前端附件 API 不完整");
assert(myBatisConfig.includes("com.cunzhi.governance.attachment.mapper"), "MyBatis 扫描范围缺少附件 Mapper");
assert(accessMigration.includes("security_version") && accessMigration.includes("sys_role") && accessMigration.includes("sys_menu"), "Flyway V5 缺少权限版本或会话安全版本");
assert(
  passwordMigration.includes("password_change_required") && passwordMigration.includes("idx_sys_user_password_change_required"),
  "Flyway V8 缺少强制改密字段或索引"
);
assert(
  gridWorkerNavigationMigration.includes("GRID_SCOPE_READ") &&
    gridWorkerNavigationMigration.includes("RESIDENT_SCOPE_READ") &&
    gridWorkerNavigationMigration.includes("role.role_code = 'GRID_WORKER'") &&
    gridWorkerNavigationMigration.includes("menu.menu_code in ('GRID', 'RESIDENT')"),
  "Flyway V9 缺少网格员导航与底层只读权限分离"
);
assert(
  sensitiveAuditMigration.includes("resident_sensitive_access_log") &&
    sensitiveAuditMigration.includes("operator_user_id") &&
    sensitiveAuditMigration.includes("result_count"),
  "Flyway V6 缺少居民敏感字段访问审计表或关键字段"
);
assert(
  governanceMigration.includes("create table task_attachment") &&
    governanceMigration.includes("event_category") &&
    governanceMigration.includes("scope_grid_id") &&
    governanceMigration.includes("upload_token") &&
    governanceMigration.includes("uk_event_attachment_upload_token") &&
    governanceMigration.includes("uk_task_attachment_upload_token") &&
    governanceMigration.includes("event:category:manage") &&
    governanceMigration.includes("file:delete") &&
    governanceMigration.includes("resident:sensitive:audit:read"),
  "Flyway V7 缺少任务附件、类别版本、审计范围或新增权限"
);
assert(
  governanceMigration.includes("file_purged_at") &&
    governanceMigration.includes("idx_event_attachment_pending_purge") &&
    governanceMigration.includes("idx_task_attachment_pending_purge") &&
    governanceMigration.includes("ck_event_attachment_file_purge") &&
    governanceMigration.includes("ck_task_attachment_file_purge"),
  "Flyway V7 缺少附件物理文件待清理标记、索引或约束"
);
assert(
  !/\bupdate\s+resident_sensitive_access_log\b[\s\S]*?\bscope_grid_id\s*=\s*(?:\w+\.)?grid_id\b/i.test(governanceMigration) &&
    apiContract.includes("V6 的空范围历史保持 `NULL`") &&
    spec.includes("不能用居民当前网格回填") &&
    databaseReadme.includes("不根据居民当前网格回填"),
  "V7 不得以居民当前网格回填历史敏感审计范围，文档也必须说明该边界"
);
assert(systemAccessService.includes("CORE_ROLES") && systemAccessService.includes("bumpUserSecurityVersionsForRole"), "固定核心角色安全护栏不完整");
assert(sessionFreshnessFilter.includes("securityVersion()") && sessionFreshnessFilter.includes("请重新登录"), "旧 Session 即时失效门禁不完整");
assert(frontendRouter.includes("system/roles") && frontendRouter.includes("system/menus"), "前端缺少角色或菜单管理路由");

const tablePositions = Object.fromEntries(tableMatches.map((match) => [match[1], match.index]));
const foreignKeys = [...migration.matchAll(/foreign\s+key\s*\([^)]*\)\s*references\s+([a-z0-9_]+)/gi)];
for (const foreignKey of foreignKeys) {
  const owner = tableMatches.filter((table) => table.index < foreignKey.index).at(-1)?.[1];
  const target = foreignKey[1];
  assert(target in tablePositions, `外键引用未知表：${target}`);
  assert(tablePositions[target] <= tablePositions[owner], `外键创建顺序错误：${owner} -> ${target}`);
}

const namedDatabaseObjects = [
  ...migration.matchAll(/constraint\s+([a-z0-9_]+)/gi),
  ...migration.matchAll(/\b(?:unique\s+)?key\s+([a-z0-9_]+)\s*\(/gi),
].map((match) => match[1]);
const duplicateDatabaseObjects = namedDatabaseObjects.filter(
  (name, index) => namedDatabaseObjects.indexOf(name) !== index
);
assert(duplicateDatabaseObjects.length === 0, `约束或索引重名：${[...new Set(duplicateDatabaseObjects)].join(", ")}`);
assert(namedDatabaseObjects.every((name) => name.length <= 64), "存在超过 MySQL 64 字符限制的约束或索引名");

let parenthesisDepth = 0;
for (const character of migration.replace(/'[^']*'/g, "''")) {
  if (character === "(") parenthesisDepth += 1;
  if (character === ")") parenthesisDepth -= 1;
  if (parenthesisDepth < 0) break;
}
assert(parenthesisDepth === 0, "Flyway V1 括号未平衡");

for (const table of effectiveBusinessTables) {
  assert(spec.includes(`\`${table}\``), `功能设计未映射物理表：${table}`);
}

const eventStatuses = [
  "REPORTED",
  "ACCEPTED",
  "ASSIGNED",
  "PROCESSING",
  "PENDING_REVIEW",
  "CLOSED",
  "REJECTED",
  "CANCELLED",
];
const taskStatuses = ["PENDING_ACCEPT", "PROCESSING", "PENDING_REVIEW", "COMPLETED", "CANCELLED"];
for (const status of new Set([...eventStatuses, ...taskStatuses])) {
  assert(migration.includes(`'${status}'`), `Flyway V1 缺少状态：${status}`);
  assert(spec.includes(status), `功能设计缺少状态：${status}`);
}

const expectedPermissions = [
  "system:user:manage",
  "system:role:manage",
  "system:menu:manage",
  "grid:read",
  "grid:write",
  "grid:assign",
  "resident:read",
  "resident:write",
  "resident:sensitive:read",
  "resident:portal",
  "event:read",
  "event:report",
  "event:accept",
  "event:reject",
  "event:assign",
  "event:cancel",
  "task:read",
  "task:create",
  "task:accept",
  "task:handle",
  "task:review",
  "task:cancel",
  "file:read",
  "file:upload",
  "file:delete",
  "dashboard:read",
  "event:category:manage",
  "resident:sensitive:audit:read",
];
const roleWorkbenchPermissions = new Set(
  Object.values(roleWorkbenchMatrix?.roles || {}).flatMap((role) => [
    role.statsApi?.permission,
    ...(role.navigation || []).map((entry) => entry.permission),
    ...(role.writeGroups || []).flatMap((group) => (group.operations || []).map((operation) => operation.permission)),
  ]).filter(Boolean)
);
const frozenPermissions = new Set([
  ...expectedPermissions,
  ...(roleWorkbenchMatrix?.permissions || []),
  ...roleWorkbenchPermissions,
]);
for (const permission of expectedPermissions) {
  assert(migrationChain.includes(`'${permission}'`), `Flyway 权限种子缺少：${permission}`);
  assert(apiContract.includes(permission), `API 契约缺少权限：${permission}`);
}

for (const role of ["SYSTEM_ADMIN", "COMMUNITY_STAFF", "GRID_WORKER", "RESIDENT"]) {
  assert(migrationChain.includes(`'${role}'`), `Flyway 角色种子缺少：${role}`);
  assert(spec.includes(role) || apiContract.includes(role), `设计文档缺少角色：${role}`);
}

const backendJavaFiles = walk("backend/src/main/java").filter((file) => file.endsWith(".java"));
const backendTestFiles = walk("backend/src/test/java").filter((file) => file.endsWith("Test.java"));
const backendJava = backendJavaFiles.map((file) => fs.readFileSync(file, "utf8")).join("\n");
for (const testFile of [
  "backend/src/test/java/com/cunzhi/governance/dashboard/service/DashboardServiceTest.java",
  "backend/src/test/java/com/cunzhi/governance/event/service/EventCategoryServiceTest.java",
  "backend/src/test/java/com/cunzhi/governance/task/service/TaskAttachmentServiceTest.java",
  "backend/src/test/java/com/cunzhi/governance/attachment/service/EventAttachmentServiceTest.java",
  "backend/src/test/java/com/cunzhi/governance/attachment/service/AttachmentPurgeServiceTest.java",
  "backend/src/test/java/com/cunzhi/governance/resident/service/ResidentServiceTest.java"
]) {
  requireFile(testFile);
}
assert(backendJavaFiles.length > 0, "后端 Java 骨架为空");
assert(backendTestFiles.length >= 2, "后端缺少事件/任务状态机测试骨架");
assert(!/csrf\s*\([^)]*disable|csrf\s*\(\s*\)\s*\.\s*disable/i.test(backendJava), "后端关闭了 CSRF");
assert(!/SessionCreationPolicy\.STATELESS/.test(backendJava), "后端错误使用无状态会话");
const deleteMappingFiles = backendJavaFiles.filter((file) => fs.readFileSync(file, "utf8").includes("@DeleteMapping"));
const allowedDeleteControllers = new Set([
  "EventAttachmentController.java",
  "TaskAttachmentController.java",
  "ResidentPortalController.java",
]);
assert(deleteMappingFiles.length === 3, `附件软删除接口数量异常：${deleteMappingFiles.map(relative).join(", ")}`);
assert(
  deleteMappingFiles.every((file) => allowedDeleteControllers.has(path.basename(file))),
  "DELETE 只能用于受约束的事件/任务/居民附件软删除接口"
);
assert(
  backendJava.includes("CookieCsrfTokenRepository") || /\.csrf\(\s*csrf\s*->\s*csrf\.spa\(\)/s.test(backendJava),
  "后端未配置 SPA CSRF"
);
assert(securityConfig.includes('"DELETE"'), "CORS 必须开放已使用的 DELETE 方法");
assert(!/\.taskVersion\(\)/.test(taskActionRequest), "TaskActionRequest 不存在 taskVersion accessor");
assert(
  authService.includes("userAuthMapper.updateLastLoginAt") &&
    userAuthMapper.includes("last_login_at = current_timestamp(3)"),
  "登录成功未回写最近登录时间"
);
assert(
  authController.includes('@GetMapping("/navigation")') &&
    authService.includes("findEnabledNavigationMenus") &&
    userAuthMapper.includes("m.menu_type = 'MENU'") &&
    userAuthMapper.includes("m.status = 'ENABLED'") &&
    userAuthMapper.includes("r.status = 'ENABLED'"),
  "动态导航未限定当前用户、启用角色和启用 MENU"
);
for (const field of ["id", "code", "name", "routePath", "icon", "sortNo"]) {
  assert(navigationItem.includes(field), `NavigationItem 缺少冻结字段：${field}`);
}
assert(
  /where e\.status = 'REPORTED'\) as pendingEventCount/.test(dashboardMapper),
  "看板待受理事件口径必须只统计 REPORTED"
);
assert(
  /where area_type = 'GRID'\s+and status = 'ENABLED'/.test(dashboardMapper),
  "看板有效网格必须过滤 ENABLED"
);
assert(
  dashboardMapper.includes("'ACCEPTED', 'ASSIGNED', 'PROCESSING'") &&
    dashboardMapper.includes("completed_at &lt;= t.due_at") &&
    dashboardMapper.includes("t.status = 'COMPLETED'") &&
    dashboardMapper.includes("limit 10"),
  "看板 D2/D3/D4 SQL 口径不完整"
);
for (const [source, fields, label] of [
  [dashboardOverview, ["gridEventStats", "categoryStats", "recentEvents"], "DashboardOverview"],
  [dashboardGridEventStat, ["gridId", "gridCode", "gridName", "eventCount", "completedWithDeadlineCount", "onTimeClosedCount", "onTimeCompletionRate"], "DashboardGridEventStat"],
  [dashboardCategoryStat, ["categoryId", "categoryName", "eventCount", "percentage"], "DashboardCategoryStat"],
  [dashboardRecentEvent, ["id", "eventNo", "title", "categoryName", "gridName", "status", "severity", "reportedAt"], "DashboardRecentEvent"]
]) {
  for (const field of fields) assert(source.includes(field), `${label} 缺少冻结字段：${field}`);
}
assert(
  dashboardService.includes("multiply(BigDecimal.valueOf(100))") && dashboardService.includes("BigDecimal.ZERO.setScale(2)"),
  "看板百分比必须返回 0..100，且无分母为 0"
);
assert(
  eventCategoryController.includes('/api/system/event-categories') &&
    eventCategoryService.includes("countNonTerminalEvents") &&
    eventCategoryService.includes("OPTIMISTIC_LOCK_CONFLICT"),
  "事件类别 CRUD、在用停用保护或乐观锁不完整"
);
assert(
  taskAttachmentController.includes('/api/tasks/{taskId}/attachments') &&
    taskAttachmentController.includes('/{attachmentId}/content') &&
    taskAttachmentController.includes('requestToken') &&
    taskAttachmentService.includes("仅任务执行人可以上传附件") &&
    taskAttachmentService.includes("countActiveByTaskId") &&
    taskAttachmentService.includes("findIdempotentAttachment") &&
    taskAttachmentService.includes("softDelete"),
  "任务附件的归属、上传或软删除约束不完整"
);
assert(
  attachmentController.includes('@DeleteMapping("/{attachmentId}")') &&
    attachmentController.includes('requestToken') &&
    attachmentService.includes("deleteForResident") &&
    attachmentService.includes("findIdempotentAttachment") &&
    attachmentService.includes("只能访问本人上报的事件附件") &&
    attachmentService.includes("当前事件状态不允许删除附件"),
  "事件/居民附件删除与本人范围保护不完整"
);
assert(
  eventAttachmentMapper.includes("findActiveByEventAndUploaderAndUploadToken") &&
    taskAttachmentMapper.includes("findActiveByTaskAndUploaderAndUploadToken") &&
    eventAttachmentServiceTest.includes("RepeatedActiveRequestToken") &&
    taskAttachmentServiceTest.includes("RepeatedActiveRequestToken"),
  "附件请求令牌的 Mapper 或服务层回归测试不完整"
);
assert(
  eventAttachmentMapper.includes("findPendingFilePurges") && eventAttachmentMapper.includes("markFilePurged") &&
    taskAttachmentMapper.includes("findPendingFilePurges") && taskAttachmentMapper.includes("markFilePurged") &&
    attachmentPurgeService.includes("retryPendingAttachments") &&
    attachmentPurgeService.includes("purgeEventAttachment") && attachmentPurgeService.includes("purgeTaskAttachment") &&
    attachmentPurgeMarkerService.includes("Propagation.REQUIRES_NEW") &&
    attachmentPurgeScheduler.includes("@Scheduled(initialDelay = 10_000L, fixedDelay = 60_000L)") &&
    attachmentPurgeScheduler.includes("retryPendingAttachmentFiles") &&
    governanceApplication.includes("@EnableScheduling") &&
    attachmentService.includes("purgeService.purgeEventAttachment") &&
    taskAttachmentService.includes("purgeService.purgeTaskAttachment") &&
    eventAttachmentServiceTest.includes("rollbackDoesNotTriggerDeferredFilePurge") &&
    taskAttachmentServiceTest.includes("directDeleteSoftDeletesThenRemovesPhysicalFileWithoutStaging") &&
    attachmentPurgeServiceTest.includes("marksTaskAttachmentPurgedWhenItsPhysicalFileIsAlreadyMissing") &&
    attachmentPurgeServiceTest.includes("retainsPendingMarkerWhenPhysicalDeletionFails") &&
    attachmentPurgeServiceTest.includes("retriesBothPendingQueuesWithinTheConfiguredBatchLimit") &&
    attachmentPurgeServiceTest.includes("AttachmentPurgeService.DEFAULT_BATCH_LIMIT"),
  "附件软删后的持久化清理标记、事务后清理或定时重试不完整"
);
assert(
  residentController.includes('/sensitive-access-logs') && residentController.includes("CacheControl.noStore") &&
    sensitiveAccessView.includes("residentNo") && sensitiveAccessView.includes("purpose") &&
    sensitiveAccessMapper.includes("l.purpose like") &&
    sensitiveAccessMapper.includes("l.scope_grid_id in") &&
    sensitiveAccessMapper.includes("l.scope_grid_id is null and l.operator_user_id = #{currentUserId}") &&
    !sensitiveAccessMapper.includes("r.grid_id in"),
  "敏感审计的 no-store、用途检索、严格历史范围或脱敏投影不完整"
);
assert(
  /(?:household\.)?status(?:\s+as\s+\w+)?\s*,\s*(?:household\.)?version(?:\s+as\s+\w+)?/i.test(householdMapper),
  "家庭户查询未返回 version"
);
assert(/\bint version\b/.test(householdSummary), "HouseholdSummary 未暴露 version");
assert(/u\.status,[\s\S]{0,600}u\.version/i.test(systemUserMapper), "用户查询未返回 version");
assert(/\bint version\b/.test(userSummary), "UserSummary 未暴露 version");

for (const sourceFile of backendJavaFiles) {
  const source = fs.readFileSync(sourceFile, "utf8");
  let braces = 0;
  for (const character of source.replace(/"(?:\\.|[^"\\])*"/gs, '""')) {
    if (character === "{") braces += 1;
    if (character === "}") braces -= 1;
    if (braces < 0) break;
  }
  assert(braces === 0, `Java 花括号未平衡：${relative(sourceFile)}`);
}

const permissionAnnotations = [...backendJava.matchAll(/hasPermission\('([^']+)'\)/g)].map((match) => match[1]);
for (const permission of permissionAnnotations) {
  assert(frozenPermissions.has(permission), `后端注解使用未冻结权限：${permission}`);
}
const permissionCodesFile = backendJavaFiles.find((file) => path.basename(file) === "PermissionCodes.java");
assert(Boolean(permissionCodesFile), "后端缺少 PermissionCodes");
if (permissionCodesFile) {
  const source = fs.readFileSync(permissionCodesFile, "utf8");
  for (const permission of expectedPermissions) {
    assert(source.includes(`"${permission}"`), `PermissionCodes 缺少：${permission}`);
  }
  for (const permission of roleWorkbenchPermissions) {
    assert(source.includes(`"${permission}"`), `PermissionCodes 缺少角色工作台权限：${permission}`);
  }
}

for (const controller of backendJavaFiles.filter((file) => file.endsWith("Controller.java"))) {
  const source = fs.readFileSync(controller, "utf8");
  assert(!/import\s+[\w.]+\.mapper\./.test(source), `Controller 直接依赖 Mapper：${relative(controller)}`);
}

const eventStatusFile = backendJavaFiles.find((file) => path.basename(file) === "EventStatus.java");
const taskStatusFile = backendJavaFiles.find((file) => path.basename(file) === "TaskStatus.java");
assert(Boolean(eventStatusFile), "后端缺少 EventStatus 枚举");
assert(Boolean(taskStatusFile), "后端缺少 TaskStatus 枚举");
if (eventStatusFile) {
  const source = fs.readFileSync(eventStatusFile, "utf8");
  for (const status of eventStatuses) assert(new RegExp(`\\b${status}\\b`).test(source), `EventStatus 缺少 ${status}`);
}
if (taskStatusFile) {
  const source = fs.readFileSync(taskStatusFile, "utf8");
  for (const status of taskStatuses) assert(new RegExp(`\\b${status}\\b`).test(source), `TaskStatus 缺少 ${status}`);
}

assert(applicationYaml.includes("${DB_URL:"), "数据库地址未通过环境变量配置");
assert(applicationYaml.includes("${DB_USERNAME:"), "数据库用户名未通过环境变量配置");
assert(applicationYaml.includes("${DB_PASSWORD:"), "数据库密码未通过环境变量配置");
assert(applicationYaml.includes("${BOOTSTRAP_ADMIN_PASSWORD:}"), "首个管理员密码未通过空默认值环境变量配置");
assert(!/password:\s+(?!\$\{)[^\s#]+/i.test(applicationYaml), "application.yml 疑似含明文密码");
assert(!/spring:\s*[\s\S]*sql:\s*[\s\S]*init:/i.test(applicationYaml), "不应同时启用 spring.sql.init");

const frontendFiles = walk("frontend/src").filter((file) => /\.(js|vue)$/.test(file));
const frontendSource = frontendFiles.map((file) => fs.readFileSync(file, "utf8")).join("\n");
const frontendVueSources = frontendFiles
  .filter((file) => file.endsWith(".vue"))
  .map((file) => fs.readFileSync(file, "utf8"));
const elementUiContract = reconcileElementUiRegistration(frontendMain, frontendVueSources);
for (const issue of elementUiContract.issues) assert(false, `Element UI 按需注册对账失败：${issue}`);
assert(frontendFiles.length > 0, "前端源码骨架为空");
assert(!/import\s+ElementUI\s+from\s+['"]element-ui['"]/.test(frontendSource), "前端仍在全量导入 Element UI");
assert(!/theme-chalk\/index\.css/.test(frontendSource), "前端仍在全量导入 Element UI 样式");
assert(!/\blocalStorage\b|\bsessionStorage\b/.test(frontendSource), "前端不得保存本地认证令牌");
assert(!/\bBearer\b/i.test(frontendSource), "前端不应混入 Bearer Token 认证");
assert(/withCredentials\s*:\s*true/.test(frontendSource), "Axios 未启用 withCredentials");
assert(frontendSource.includes("to.meta.nav && !isUsableNavigationPath"), "前端直达路由未服从服务端导航白名单");
assert(
  frontendSessionStore.includes("await dispatch('navigation/refresh'") &&
    frontendSessionStore.includes("commit('navigation/CLEAR_NAVIGATION'"),
  "前端登录/退出未刷新或清理跨角色导航缓存"
);
assert(/xsrfCookieName\s*:\s*['\"]XSRF-TOKEN['\"]/.test(frontendSource), "Axios CSRF Cookie 名不一致");
assert(/xsrfHeaderName\s*:\s*['\"]X-XSRF-TOKEN['\"]/.test(frontendSource), "Axios CSRF Header 名不一致");
for (const endpoint of ["/auth/csrf", "/auth/login", "/auth/me", "/auth/password", "/auth/logout"]) {
  assert(frontendSource.includes(endpoint), `前端认证 API 缺少：${endpoint}`);
}
for (const endpoint of [
  "/assign",
  "/cancel",
  "/submit-review",
  "/review",
]) {
  assert(frontendSource.includes(endpoint), `前端业务 API 缺少：${endpoint}`);
  assert(backendJava.includes(endpoint), `后端业务 API 缺少：${endpoint}`);
}
assert(frontendSource.includes("/dashboard/overview"), "前端业务 API 缺少：/dashboard/overview");
assert(
  backendJava.includes('"/api/dashboard"') && backendJava.includes('"/overview"'),
  "后端业务 API 缺少：/api/dashboard/overview"
);
for (const endpoint of [
  "/auth/navigation",
  "/system/event-categories",
  "/tasks/${String(taskId)}/attachments",
  "/resident-portal/events/${String(eventId)}/attachments",
  "/sensitive-access-logs"
]) {
  assert(frontendSource.includes(endpoint), `前端业务 API 缺少：${endpoint}`);
}
assert(
  frontendRouter.includes("path: '/'") && frontendRouter.includes("to.path === '/'") &&
    navigationStore.includes("getNavigation") &&
    appLayout.includes("routePath") && appLayout.includes("已注册路由为白名单") &&
    loginView.includes("resolveHomePath") && appLayout.includes("resolveHomePath") &&
    navigationUtils.includes("resolveHomePath") && navigationUtils.includes("navigation.status === 'ready'") &&
    navigationUtils.includes("navigation.items.find") &&
    navigationUtils.includes("return first ? first.routePath : '/forbidden'") &&
    navigationUtils.includes("navigation.status === 'error'") &&
    (navigationUtils.includes("return registered.length ? registered[0].path : '/forbidden'") ||
      navigationUtils.includes("if (navigation.status === 'error') return '/forbidden'")) &&
    !frontendSource.includes("router.addRoutes"),
  "根路由跳转或服务端导航白名单渲染不完整"
);
assert(
  frontendFilesApi.includes("deleteEventAttachment") &&
    residentPortalApi.includes("deleteResidentEventAttachment") &&
    taskApi.includes("listTaskAttachments") && taskApi.includes("deleteTaskAttachment") &&
    frontendFilesApi.includes("uploadTokenFor(file)") &&
    residentPortalApi.includes("uploadTokenFor(file)") &&
    taskApi.includes("uploadTokenFor(file)") &&
    uploadTokenUtils.includes("WeakMap") && uploadTokenUtils.includes("randomUUID"),
  "前端事件、居民或任务附件 API 不完整"
);
for (const obsolete of ["/dispatch", "/submit\"", "MOVED_OUT", "INACTIVE"]) {
  assert(!frontendSource.includes(obsolete), `前端仍含旧契约值：${obsolete}`);
}

for (const sourceFile of frontendFiles.filter((file) => file.endsWith(".js"))) {
  const source = fs.readFileSync(sourceFile, "utf8");
  for (const match of source.matchAll(/(?:from\s+|import\s*\()\s*['"](\.[^'"]+)['"]/g)) {
    const base = path.resolve(path.dirname(sourceFile), match[1]);
    const candidates = [
      base,
      `${base}.js`,
      `${base}.vue`,
      path.join(base, "index.js"),
    ];
    assert(candidates.some((candidate) => fs.existsSync(candidate)), `前端相对导入不存在：${relative(sourceFile)} -> ${match[1]}`);
  }
}

const sqlFiles = walk(".").filter(
  (file) =>
    file.endsWith(".sql") &&
    !relative(file).startsWith(".git/") &&
    !relative(file).includes("/target/") &&
    !relative(file).includes("/node_modules/")
);
const sqlRelativePaths = sqlFiles.map(relative).sort();
const flywayPrefix = "backend/src/main/resources/db/migration/";
assert(sqlRelativePaths.includes(migrationPath), "Flyway 迁移链缺少 V1__baseline.sql");
assert(sqlRelativePaths.includes(registrationMigrationPath), "Flyway 迁移链缺少 V3 注册迁移");
assert(sqlRelativePaths.includes(privacyMigrationPath), "Flyway 迁移链缺少 V4 居民注册隐私迁移");
assert(sqlRelativePaths.includes(accessMigrationPath), "Flyway 迁移链缺少 V5 权限与会话迁移");
assert(sqlRelativePaths.includes(sensitiveAuditMigrationPath), "Flyway 迁移链缺少 V6 居民敏感字段访问审计迁移");
assert(sqlRelativePaths.includes(governanceMigrationPath), "Flyway 迁移链缺少 V7 治理附件、导航与审计迁移");
assert(sqlRelativePaths.includes(roleWorkbenchMigrationPath), "Flyway 迁移链缺少 V10 角色工作台迁移");
assert(sqlRelativePaths.includes(operationAuditMigrationPath), "Flyway 迁移链缺少 V11 操作审计迁移");
assert(sqlRelativePaths.includes(openingNavigationMigrationPath), "Flyway 迁移链缺少 V12 开题可见导航迁移");
assert(
  sqlRelativePaths.every((file) => file.startsWith(flywayPrefix)),
  `SQL 必须全部位于 Flyway 迁移目录，实际为：${sqlRelativePaths.join(", ")}`
);
const migrationVersions = sqlRelativePaths.map((file) => {
  const name = path.basename(file);
  const match = name.match(/^V(\d+)__[a-z0-9_]+\.sql$/i);
  assert(Boolean(match), `Flyway 迁移命名不合法：${file}`);
  return match ? Number(match[1]) : Number.NaN;
});
assert(
  migrationVersions.every(Number.isInteger) && new Set(migrationVersions).size === migrationVersions.length,
  `Flyway 版本必须为不重复整数：${sqlRelativePaths.join(", ")}`
);

function apiFragments(apiPath) {
  return String(apiPath || "")
    .split("/")
    .filter(Boolean)
    .filter((part) => !/^\{[^}]+\}$/.test(part))
    .filter((part) => part !== "api");
}

function sourceHasApi(source, apiPath) {
  const fragments = apiFragments(apiPath);
  return fragments.length > 0 && fragments.every((fragment) => source.includes(fragment));
}

function sourceHasRoute(source, routePath) {
  const normalized = String(routePath || "").replace(/^\/+/, "");
  const leaf = normalized.split("/").pop();
  return source.includes(routePath) ||
    source.includes(`path: '${normalized}'`) ||
    source.includes(`path: "${normalized}"`) ||
    source.includes(`path: '${leaf}'`) ||
    source.includes(`path: "${leaf}"`);
}

const roleWorkbenchRoles = roleWorkbenchMatrix?.roles || {};
const roleWorkbenchRoleCodes = roleWorkbenchMatrix?.requiredRoles || [];
const roleWorkbenchSchemaSource = `${migrationChain}\n${backendJava}`.toLowerCase();
const roleWorkbenchStatsFields = roleWorkbenchMatrix?.statsFields || [];
const roleWorkbenchRuntimeTables = new Set(["flyway_schema_history"]);
assert(roleWorkbenchMatrix?.contractVersion === 1, "角色工作台合同缺少 contractVersion=1");
assert(roleWorkbenchRoleCodes.length === 4, "角色工作台合同必须精确包含四个角色");
assert(
  roleWorkbenchMatrix?.minimumNavigationEntries >= 4,
  "角色工作台合同的入口最低数量必须至少为 4"
);
for (const roleCode of roleWorkbenchRoleCodes) {
  const role = roleWorkbenchRoles[roleCode];
  const navigation = role?.navigation || [];
  const hiddenRoutes = role?.hiddenRoutes || [];
  const expectedCount = roleWorkbenchMatrix?.exactNavigationCounts?.[roleCode];
  assert(Boolean(role), `角色工作台合同缺少角色：${roleCode}`);
  assert(navigation.length >= (roleWorkbenchMatrix?.minimumNavigationEntries || 4), `${roleCode} 导航入口少于 4 个`);
  assert(navigation.length === expectedCount, `${roleCode} 导航入口数量应为 ${expectedCount}，实际为 ${navigation.length}`);
  assert(new Set(navigation.map((item) => item.code)).size === navigation.length, `${roleCode} 导航编码重复`);
  assert(new Set(navigation.map((item) => item.routePath)).size === navigation.length, `${roleCode} 导航路由重复`);
  assert(
    hiddenRoutes.every((routePath) => !navigation.some((item) => item.routePath === routePath)),
    `${roleCode} 开题隐藏路由仍出现在导航合同中`
  );
  assert((role?.writeGroups || []).length >= 2, `${roleCode} 主责写操作组少于 2 组`);
  assert((role?.stateTransitions || []).length >= 2, `${roleCode} 状态流转合同少于 2 条`);
  for (const transition of role?.stateTransitions || []) {
    assert(/^([A-Z_]+|NONE)->([A-Z_]+)$/.test(transition), `${roleCode} 状态流转格式非法：${transition}`);
  }
  assert(role?.statsApi?.path, `${roleCode} 缺少角色统计 API`);
  for (const field of roleWorkbenchStatsFields) {
    assert(backendJava.includes(field), `${roleCode} 统计后端缺少字段：${field}`);
    assert(frontendSource.includes(field), `${roleCode} 统计前端缺少字段：${field}`);
  }
  assert(sourceHasApi(backendJava, role.statsApi?.path), `${roleCode} 统计后端 API 缺失：${role.statsApi?.path}`);
  assert(sourceHasApi(frontendSource, role.statsApi?.path), `${roleCode} 统计前端 API 缺失：${role.statsApi?.path}`);
  assert(frozenPermissions.has(role.statsApi?.permission), `${roleCode} 统计权限未冻结：${role.statsApi?.permission}`);
  assert(migrationChain.includes(`'${role.statsApi?.permission}'`), `${roleCode} 统计权限未种入迁移`);
  for (const entry of navigation) {
    assert(entry.code && entry.routePath && entry.permission && entry.readApi, `${roleCode} 存在不完整导航合同项`);
    assert(sourceHasRoute(frontendRouter, entry.routePath), `${roleCode}/${entry.code} 前端路由缺失：${entry.routePath}`);
    assert(migrationChain.includes(`'${entry.code}'`), `${roleCode}/${entry.code} 未种入 sys_menu`);
    assert(migrationChain.includes(`'${entry.routePath}'`), `${roleCode}/${entry.code} 迁移路由与前端合同不一致：${entry.routePath}`);
    assert(migrationChain.includes(`'${entry.permission}'`), `${roleCode}/${entry.code} 菜单权限未种入迁移`);
    assert(sourceHasApi(frontendSource, entry.readApi), `${roleCode}/${entry.code} 前端读取 API 缺失：${entry.readApi}`);
    assert(sourceHasApi(backendJava, entry.readApi), `${roleCode}/${entry.code} 后端读取 API 缺失：${entry.readApi}`);
    for (const table of entry.dbTables || []) {
      assert(roleWorkbenchRuntimeTables.has(table) || roleWorkbenchSchemaSource.includes(table.toLowerCase()), `${roleCode}/${entry.code} DB 表缺失：${table}`);
    }
    for (const state of entry.states || []) {
      assert(roleWorkbenchSchemaSource.includes(`'${state.toLowerCase()}'`) || roleWorkbenchSchemaSource.includes(state.toLowerCase()), `${roleCode}/${entry.code} 状态缺失：${state}`);
    }
  }
  for (const group of role.writeGroups || []) {
    assert(group.id && Array.isArray(group.operations) && group.operations.length >= 2, `${roleCode} 写操作组结构不完整：${group.id || "unknown"}`);
    for (const operation of group.operations || []) {
      assert(operation.id && operation.method && operation.path && operation.permission, `${roleCode} 写操作合同结构不完整`);
      assert(operation.probeable === false || Object.hasOwn(operation, "probeBody"), `${roleCode}/${operation.id} 缺少合法权限探针请求体`);
      assert(sourceHasApi(frontendSource, operation.path), `${roleCode}/${operation.id} 前端写 API 缺失：${operation.path}`);
      assert(sourceHasApi(backendJava, operation.path), `${roleCode}/${operation.id} 后端写 API 缺失：${operation.path}`);
      assert(frozenPermissions.has(operation.permission), `${roleCode}/${operation.id} 写权限未冻结：${operation.permission}`);
      assert(migrationChain.includes(`'${operation.permission}'`), `${roleCode}/${operation.id} 写权限未种入迁移`);
      for (const table of operation.tables || []) {
        assert(roleWorkbenchRuntimeTables.has(table) || roleWorkbenchSchemaSource.includes(table.toLowerCase()), `${roleCode}/${operation.id} DB 表缺失：${table}`);
      }
      for (const state of operation.states || []) {
        assert(roleWorkbenchSchemaSource.includes(`'${state.toLowerCase()}'`) || roleWorkbenchSchemaSource.includes(state.toLowerCase()), `${roleCode}/${operation.id} 状态缺失：${state}`);
      }
      for (const forbiddenRole of operation.forbiddenRoles || []) {
        assert(roleWorkbenchRoleCodes.includes(forbiddenRole), `${roleCode}/${operation.id} 越权角色未在合同登记：${forbiddenRole}`);
        assert(forbiddenRole !== roleCode, `${roleCode}/${operation.id} 不能把自身列为越权角色`);
      }
    }
  }
}

for (const markdown of [
  "README.md",
  "docs/functional-spec.md",
  "docs/api-contract.md",
  "docs/development.md",
  "docs/thesis/README.md",
  "docs/thesis/chapter-system-implementation-and-testing.md",
  "database/README.md",
  "frontend/README.md",
]) {
  const source = requireFile(markdown);
  const fences = (source.match(/```/g) || []).length;
  assert(fences % 2 === 0, `Markdown 代码围栏未闭合：${markdown}`);
  for (const link of source.matchAll(/\[[^\]]+\]\(([^)]+)\)/g)) {
    if (/^[a-z]+:\/\//i.test(link[1])) continue;
    const target = path.resolve(path.dirname(path.join(root, markdown)), link[1]);
    assert(fs.existsSync(target), `Markdown 链接不存在：${markdown} -> ${link[1]}`);
  }
}

if (errors.length > 0) {
  console.error(`静态校验失败（${errors.length} 项）：`);
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log(
  JSON.stringify(
    {
      result: "PASS",
      backendJavaFiles: backendJavaFiles.length,
      backendTestFiles: backendTestFiles.length,
      frontendSourceFiles: frontendFiles.length,
      tables: migrationBusinessTables.length,
      migrations: sqlRelativePaths.length,
      foreignKeys: foreignKeys.length,
      namedConstraintsAndIndexes: namedDatabaseObjects.length,
      permissions: frozenPermissions.size,
      legacyPermissions: expectedPermissions.length,
      roleWorkbenchContractPermissions: roleWorkbenchPermissions.size,
      elementUiTemplateTags: elementUiContract.usedTags.size,
      elementUiRegistrationContract: "positive-and-negative-self-test-pass",
      apiSmokeScript: true,
      browserE2eScript: true,
      registrationUiE2eScript: true,
      isolatedValidationPipeline: true,
      demoDataScript: true,
      thesisScreenshotScript: true,
      thesisFigurePipeline: true,
      roleWorkbenchMatrix: {
        contractVersion: roleWorkbenchMatrix?.contractVersion || null,
        navigationCounts: Object.fromEntries(
          roleWorkbenchRoleCodes.map((roleCode) => [roleCode, roleWorkbenchRoles[roleCode]?.navigation?.length || 0])
        ),
        minimumNavigationEntries: roleWorkbenchMatrix?.minimumNavigationEntries || null,
        statsFields: roleWorkbenchStatsFields,
      },
      validationMode: "static-only",
    },
    null,
    2
  )
);
