#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
artifact_root="${PIPELINE_ARTIFACT_ROOT:-${TMPDIR:-/tmp}/community-governance-validation-pipeline}"
if [[ "${artifact_root}" != /* ]]; then
  artifact_root="${project_root}/${artifact_root}"
fi

run_stamp="$(date -u +%Y%m%dT%H%M%SZ)-$$"
database_stamp="$(date -u +%Y%m%d%H%M%S)"
artifact_dir="${artifact_root%/}/${run_stamp}"
database_name="community_governance_ci_${database_stamp}_$$"
backend_log="${artifact_dir}/backend.log"
frontend_log="${artifact_dir}/frontend.log"
attachment_dir="${artifact_dir}/attachments"

db_host="${PIPELINE_DB_HOST:-127.0.0.1}"
db_port="${PIPELINE_DB_PORT:-3306}"
db_admin_username="${PIPELINE_DB_ADMIN_USERNAME:-root}"
db_admin_password="${PIPELINE_DB_ADMIN_PASSWORD:-}"
unset PIPELINE_DB_ADMIN_USERNAME PIPELINE_DB_ADMIN_PASSWORD
allow_remote_db="${PIPELINE_ALLOW_REMOTE_DB:-0}"
confirm_remote_disposable="${PIPELINE_CONFIRM_REMOTE_DISPOSABLE:-}"
allowed_remote_db_host="${PIPELINE_ALLOWED_REMOTE_DB_HOST:-}"
keep_database="${PIPELINE_KEEP_DATABASE:-0}"

backend_pid=""
frontend_pid=""
database_created=0
migration_user_created=0
runtime_user_created=0
pipeline_completed=0
migration_username=""
runtime_username=""
migration_password=""
runtime_password=""

mkdir -p "${artifact_dir}/e2e"

fail() {
  printf '[pipeline] ERROR: %s\n' "$1" >&2
  exit 1
}

step() {
  printf '\n[pipeline] %s\n' "$1"
}

require_command() {
  local command_name="$1"
  command -v "${command_name}" >/dev/null 2>&1 || fail "缺少命令：${command_name}"
}

node_major() {
  "$1" -p 'process.versions.node.split(".")[0]' 2>/dev/null
}

resolve_node_22() {
  local configured_node="${PIPELINE_NODE_BIN:-}"
  local candidate_node=""
  local -a node_candidates=()

  if [[ -n "${configured_node}" ]]; then
    [[ -x "${configured_node}" ]] || fail "PIPELINE_NODE_BIN 不是可执行文件"
    [[ "$(node_major "${configured_node}")" == "22" ]] || fail "PIPELINE_NODE_BIN 必须指向 Node 22"
    printf '%s\n' "${configured_node}"
    return
  fi

  if command -v node >/dev/null 2>&1; then
    node_candidates+=("$(command -v node)")
  fi
  node_candidates+=(
    "/opt/homebrew/opt/node@22/bin/node"
    "/usr/local/opt/node@22/bin/node"
  )

  for candidate_node in "${node_candidates[@]}"; do
    if [[ -x "${candidate_node}" ]] && [[ "$(node_major "${candidate_node}")" == "22" ]]; then
      printf '%s\n' "${candidate_node}"
      return
    fi
  done
  fail "未找到 Node 22；请设置 PIPELINE_NODE_BIN"
}

resolve_java_home() {
  local configured_home="${PIPELINE_JAVA_HOME:-${JAVA_HOME:-}}"
  local java_version=""

  if [[ -n "${configured_home}" && -x "${configured_home}/bin/java" ]]; then
    java_version="$("${configured_home}/bin/java" -version 2>&1 | head -n 1)"
    if [[ "${java_version}" =~ version\ \"17([.\"]|$) ]]; then
      printf '%s\n' "${configured_home}"
      return
    fi
  fi

  if [[ -x /usr/libexec/java_home ]]; then
    configured_home="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [[ -n "${configured_home}" && -x "${configured_home}/bin/java" ]]; then
      printf '%s\n' "${configured_home}"
      return
    fi
  fi

  if command -v java >/dev/null 2>&1; then
    java_version="$(java -version 2>&1 | head -n 1)"
    if [[ "${java_version}" =~ version\ \"17([.\"]|$) ]]; then
      configured_home="$(cd "$(dirname "$(command -v java)")/.." && pwd -P)"
      printf '%s\n' "${configured_home}"
      return
    fi
  fi
  fail "未找到 JDK 17；请设置 PIPELINE_JAVA_HOME 或 JAVA_HOME"
}

find_free_port() {
  "${node_bin}" -e '
    const net = require("node:net");
    const server = net.createServer();
    server.listen(0, "127.0.0.1", () => {
      console.log(server.address().port);
      server.close();
    });
  '
}

validate_port() {
  local label="$1"
  local port_value="$2"
  [[ "${port_value}" =~ ^[0-9]+$ ]] || fail "${label} 必须是数字端口"
  ((port_value >= 1024 && port_value <= 65535)) || fail "${label} 必须位于 1024-65535"
}

mysql_exec() {
  MYSQL_PWD="${db_admin_password}" "${mysql_bin}" \
    --protocol=TCP \
    --host="${db_host}" \
    --port="${db_port}" \
    --user="${db_admin_username}" \
    --batch \
    --skip-column-names \
    "$@"
}

mysql_limited_exec() {
  local limited_username="$1"
  local limited_password="$2"
  shift 2
  MYSQL_PWD="${limited_password}" "${mysql_bin}" \
    --protocol=TCP \
    --host="${db_host}" \
    --port="${db_port}" \
    --user="${limited_username}" \
    --batch \
    --skip-column-names \
    "$@"
}

stop_process() {
  local process_id="$1"
  if [[ -z "${process_id}" ]] || ! kill -0 "${process_id}" 2>/dev/null; then
    return
  fi
  kill "${process_id}" 2>/dev/null || true
  for _ in {1..30}; do
    if ! kill -0 "${process_id}" 2>/dev/null; then
      wait "${process_id}" 2>/dev/null || true
      return
    fi
    sleep 0.1
  done
  kill -KILL "${process_id}" 2>/dev/null || true
  wait "${process_id}" 2>/dev/null || true
}

wait_for_service() {
  local label="$1"
  local url="$2"
  local process_id="$3"
  local log_file="$4"
  local attempt

  for ((attempt = 1; attempt <= 120; attempt += 1)); do
    if curl --silent --show-error --fail "${url}" >/dev/null 2>&1; then
      return
    fi
    if ! kill -0 "${process_id}" 2>/dev/null; then
      printf '[pipeline] %s 提前退出，末尾日志：\n' "${label}" >&2
      tail -n 80 "${log_file}" >&2 || true
      fail "${label} 启动失败"
    fi
    sleep 0.5
  done

  printf '[pipeline] %s 启动超时，末尾日志：\n' "${label}" >&2
  tail -n 80 "${log_file}" >&2 || true
  fail "${label} 在 60 秒内未就绪"
}

cleanup() {
  local exit_status="$1"
  local database_disposition="未创建"
  local migration_user_disposition="未创建"
  local runtime_user_disposition="未创建"
  local attachment_disposition="未创建"
  trap - EXIT INT TERM
  set +e

  stop_process "${frontend_pid}"
  stop_process "${backend_pid}"

  if [[ -e "${attachment_dir}" || -L "${attachment_dir}" ]]; then
    if [[ "${attachment_dir}" == "${artifact_dir}/attachments" && "${artifact_dir}" == "${artifact_root%/}/"* ]]; then
      if find "${attachment_dir}" -depth -delete >/dev/null 2>&1; then
        attachment_disposition="已删除：${attachment_dir}"
      else
        attachment_disposition="删除失败：${attachment_dir}"
        exit_status=1
      fi
    else
      attachment_disposition="安全校验拒绝删除：${attachment_dir}"
      exit_status=1
    fi
  fi

  if [[ "${database_created}" == "1" ]]; then
    if [[ "${keep_database}" == "1" ]]; then
      database_disposition="已保留：${database_name}"
      exit_status=1
    elif [[ "${database_name}" =~ ^community_governance_ci_[0-9_]+$ ]]; then
      if mysql_exec --execute="DROP DATABASE \`${database_name}\`;" >/dev/null; then
        database_disposition="已删除：${database_name}"
      else
        database_disposition="删除失败：${database_name}"
        exit_status=1
      fi
    else
      database_disposition="安全校验拒绝删除：${database_name}"
      exit_status=1
    fi
  fi

  if [[ "${migration_user_created}" == "1" ]]; then
    if [[ "${migration_username}" =~ ^cgm_[0-9_]+$ ]] &&
      mysql_exec --execute="DROP USER IF EXISTS '${migration_username}'@'%';" >/dev/null; then
      migration_user_disposition="已删除：${migration_username}"
    else
      migration_user_disposition="删除失败或安全校验拒绝：${migration_username}"
      exit_status=1
    fi
  fi
  if [[ "${runtime_user_created}" == "1" ]]; then
    if [[ "${runtime_username}" =~ ^cga_[0-9_]+$ ]] &&
      mysql_exec --execute="DROP USER IF EXISTS '${runtime_username}'@'%';" >/dev/null; then
      runtime_user_disposition="已删除：${runtime_username}"
    else
      runtime_user_disposition="删除失败或安全校验拒绝：${runtime_username}"
      exit_status=1
    fi
  fi

  if [[ "${exit_status}" == "0" && "${pipeline_completed}" == "1" ]]; then
    printf '\nPIPELINE PASS\n'
  else
    printf '\nPIPELINE FAIL\n' >&2
  fi
  printf 'artifacts=%s\n' "${artifact_dir}"
  printf 'database=%s\n' "${database_disposition}"
  printf 'migration_user=%s\n' "${migration_user_disposition}"
  printf 'runtime_user=%s\n' "${runtime_user_disposition}"
  printf 'attachments=%s\n' "${attachment_disposition}"
  exit "${exit_status}"
}

trap 'cleanup "$?"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

[[ "${allow_remote_db}" == "0" || "${allow_remote_db}" == "1" ]] || fail "PIPELINE_ALLOW_REMOTE_DB 只能为 0 或 1"
[[ "${keep_database}" == "0" || "${keep_database}" == "1" ]] || fail "PIPELINE_KEEP_DATABASE 只能为 0 或 1"
[[ -n "${db_admin_username}" ]] || fail "PIPELINE_DB_ADMIN_USERNAME 不能为空"
[[ "${db_admin_username}" != *$'\n'* && "${db_admin_password}" != *$'\n'* ]] || fail "数据库凭据不得包含换行"
validate_port "PIPELINE_DB_PORT" "${db_port}"
if [[ "${db_host}" != "127.0.0.1" && "${db_host}" != "localhost" && "${db_host}" != "::1" && "${allow_remote_db}" != "1" ]]; then
  fail "默认只允许本机数据库；连接 CI 数据库服务时需显式设置 PIPELINE_ALLOW_REMOTE_DB=1"
fi
if [[ "${db_host}" != "127.0.0.1" && "${db_host}" != "localhost" && "${db_host}" != "::1" &&
  ( "${confirm_remote_disposable}" != "YES" || "${allowed_remote_db_host}" != "${db_host}" ) ]]; then
  fail "远程数据库还必须由 PIPELINE_ALLOWED_REMOTE_DB_HOST 精确匹配，并设置 PIPELINE_CONFIRM_REMOTE_DISPOSABLE=YES"
fi

require_command curl
require_command find
require_command head
require_command mysql
require_command mvn
require_command openssl
require_command python3
require_command tail
require_command tesseract
require_command tee
require_command tr
require_command uv

node_bin="$(resolve_node_22)"
node_bin_dir="$(cd "$(dirname "${node_bin}")" && pwd -P)"
npm_bin="${node_bin_dir}/npm"
[[ -x "${npm_bin}" ]] || fail "Node 22 目录中缺少 npm"

java_home="$(resolve_java_home)"
java_bin="${java_home}/bin/java"
mysql_bin="$(command -v mysql)"
mvn_bin="$(command -v mvn)"
uv_bin="$(command -v uv)"

backend_port="${PIPELINE_BACKEND_PORT:-$(find_free_port)}"
frontend_port="${PIPELINE_FRONTEND_PORT:-$(find_free_port)}"
validate_port "PIPELINE_BACKEND_PORT" "${backend_port}"
validate_port "PIPELINE_FRONTEND_PORT" "${frontend_port}"
[[ "${backend_port}" != "${frontend_port}" ]] || fail "前后端端口不得相同"

bootstrap_username="pipeline-admin-${database_stamp}-$$"
bootstrap_password="$(openssl rand -base64 36 | tr -d '\r\n')Aa1!"
worker_password="$(openssl rand -base64 36 | tr -d '\r\n')Aa1!"
resident_username="pipeline-resident-${database_stamp}-$$"
resident_password="$(openssl rand -base64 36 | tr -d '\r\n')Aa1!"
data_encryption_key="$(openssl rand -base64 32 | tr -d '\r\n')"
migration_username="cgm_${database_stamp}_$$"
runtime_username="cga_${database_stamp}_$$"
migration_password="$(openssl rand -base64 36 | tr -d '\r\n')Aa1!"
runtime_password="$(openssl rand -base64 36 | tr -d '\r\n')Aa1!"
synthetic_id_card="${database_stamp}$(printf '%04d' "$(( $$ % 10000 ))")"
synthetic_phone="139$(printf '%08d' "$(( (10#${database_stamp: -8} + $$) % 100000000 ))")"

step "环境预检"
printf 'Node %s\n' "$("${node_bin}" --version)"
printf 'Java %s\n' "$("${java_bin}" -version 2>&1 | head -n 1)"
printf 'MySQL %s\n' "$(mysql_exec --execute='SELECT VERSION();')"
printf 'Artifacts %s\n' "${artifact_dir}"

step "静态脚手架校验"
"${node_bin}" "${project_root}/scripts/validate-scaffold.mjs" 2>&1 | tee "${artifact_dir}/static-validation.log"

step "后端测试与可执行 JAR 打包"
(
  export JAVA_HOME="${java_home}"
  export PATH="${java_home}/bin:${PATH}"
  "${mvn_bin}" -f "${project_root}/backend/pom.xml" clean package
) 2>&1 | tee "${artifact_dir}/backend-build.log"

backend_jar="${project_root}/backend/target/community-governance-backend-0.1.0-SNAPSHOT.jar"
[[ -f "${backend_jar}" ]] || fail "后端打包未生成预期 JAR"

step "前端依赖复现、lint 与生产构建"
frontend_cache_dir="${project_root}/frontend/node_modules/.cache"
if [[ -d "${frontend_cache_dir}" ]]; then
  [[ "${frontend_cache_dir}" == "${project_root}/frontend/node_modules/.cache" ]] || fail "拒绝清理未解析的前端缓存目录"
  find "${frontend_cache_dir}" -depth -delete
fi
(
  cd "${project_root}/frontend"
  export PATH="${node_bin_dir}:${PATH}"
  "${npm_bin}" ci
  "${npm_bin}" run lint
  "${npm_bin}" run build
) 2>&1 | tee "${artifact_dir}/frontend-build.log"

step "创建本轮隔离数据库"
mysql_exec --execute="CREATE DATABASE \`${database_name}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
database_created=1

step "创建本轮临时最小权限数据库用户"
mysql_exec <<SQL
CREATE USER '${migration_username}'@'%' IDENTIFIED BY '${migration_password}';
SQL
migration_user_created=1
mysql_exec <<SQL
CREATE USER '${runtime_username}'@'%' IDENTIFIED BY '${runtime_password}';
SQL
runtime_user_created=1
mysql_exec --execute="GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON \`${database_name}\`.* TO '${migration_username}'@'%';"
mysql_exec --execute="GRANT SELECT, INSERT, UPDATE, DELETE ON \`${database_name}\`.* TO '${runtime_username}'@'%';"

migration_grants="$(mysql_exec --execute="SHOW GRANTS FOR '${migration_username}'@'%';")"
runtime_grants="$(mysql_exec --execute="SHOW GRANTS FOR '${runtime_username}'@'%';")"
printf '%s\n' "${migration_grants}" >"${artifact_dir}/db-migration-grants.txt"
printf '%s\n' "${runtime_grants}" >"${artifact_dir}/db-runtime-grants.txt"
[[ "${migration_grants}" != *"ALL PRIVILEGES"* && "${migration_grants}" != *"GRANT OPTION"* && "${migration_grants}" != *"CREATE USER"* ]] ||
  fail "临时迁移用户获得了全局或 ALL PRIVILEGES 权限"
[[ "${runtime_grants}" == *"SELECT, INSERT, UPDATE, DELETE ON \`${database_name}\`.*"* ]] ||
  fail "临时运行用户授权不符合预期"
[[ "${runtime_grants}" != *"ALL PRIVILEGES"* && "${runtime_grants}" != *"GRANT OPTION"* && "${runtime_grants}" != *"CREATE USER"* && "${runtime_grants}" != *"CREATE ON"* && "${runtime_grants}" != *"DROP ON"* ]] ||
  fail "临时运行用户获得了范围外权限"

if mysql_limited_exec "${runtime_username}" "${runtime_password}" \
  --execute="SELECT COUNT(*) FROM mysql.user;" >"${artifact_dir}/db-cross-schema-denied.log" 2>&1; then
  fail "临时运行用户可以读取 mysql.user"
fi
forbidden_database="community_governance_forbidden_${database_stamp}_$$"
if mysql_limited_exec "${runtime_username}" "${runtime_password}" \
  --execute="CREATE DATABASE \`${forbidden_database}\`;" >"${artifact_dir}/db-create-denied.log" 2>&1; then
  mysql_exec --execute="DROP DATABASE IF EXISTS \`${forbidden_database}\`;" >/dev/null 2>&1 || true
  fail "临时运行用户可以创建数据库"
fi

step "启动本轮后端"
(
  unset PIPELINE_DB_ADMIN_USERNAME PIPELINE_DB_ADMIN_PASSWORD
  export SERVER_PORT="${backend_port}"
  export DB_URL="jdbc:mysql://${db_host}:${db_port}/${database_name}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
  export DB_USERNAME="${runtime_username}"
  export DB_PASSWORD="${runtime_password}"
  export SPRING_FLYWAY_URL="${DB_URL}"
  export SPRING_FLYWAY_USER="${migration_username}"
  export SPRING_FLYWAY_PASSWORD="${migration_password}"
  export DB_POOL_MIN_IDLE=1
  export DB_POOL_MAX_SIZE=4
  export FRONTEND_ORIGIN="http://127.0.0.1:${frontend_port}"
  export DATA_ENCRYPTION_KEY="${data_encryption_key}"
  export ATTACHMENT_STORAGE_ROOT="${attachment_dir}"
  export BOOTSTRAP_ADMIN_ENABLED=true
  export BOOTSTRAP_ADMIN_USERNAME="${bootstrap_username}"
  export BOOTSTRAP_ADMIN_PASSWORD="${bootstrap_password}"
  export BOOTSTRAP_ADMIN_REAL_NAME="流水线管理员"
  exec "${java_bin}" -jar "${backend_jar}"
) >"${backend_log}" 2>&1 &
backend_pid=$!
wait_for_service "后端" "http://127.0.0.1:${backend_port}/api/auth/csrf" "${backend_pid}" "${backend_log}"
mysql_exec --execute="REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${migration_username}'@'%';"
post_migration_grants="$(mysql_exec --execute="SHOW GRANTS FOR '${migration_username}'@'%';")"
printf '%s\n' "${post_migration_grants}" >"${artifact_dir}/db-migration-grants-after-startup.txt"
[[ "${post_migration_grants}" == *"USAGE ON *.*"* ]] || fail "迁移完成后未撤销临时迁移用户权限"

step "执行四类角色多账号 API 业务闭环"
(
  unset PIPELINE_DB_ADMIN_USERNAME PIPELINE_DB_ADMIN_PASSWORD
  export SMOKE_BASE_URL="http://127.0.0.1:${backend_port}"
  export SMOKE_CONFIRM_ISOLATED=YES
  export SMOKE_ADMIN_USERNAME="${bootstrap_username}"
  export SMOKE_ADMIN_PASSWORD="${bootstrap_password}"
  export SMOKE_WORKER_USERNAME="pipeline-worker"
  export SMOKE_WORKER_PASSWORD="${worker_password}"
  export SMOKE_RESIDENT_USERNAME="${resident_username}"
  export SMOKE_RESIDENT_PASSWORD="${resident_password}"
  export SMOKE_EVENT_CATEGORY_ID=1
  export SMOKE_CORS_ORIGIN="http://127.0.0.1:${frontend_port}"
  export SMOKE_SYNTHETIC_ID_CARD="${synthetic_id_card}"
  export SMOKE_SYNTHETIC_PHONE="${synthetic_phone}"
  exec "${node_bin}" "${project_root}/scripts/runtime-smoke.mjs"
) 2>&1 | tee "${artifact_dir}/api-smoke.log"

step "启动本轮前端"
(
  unset PIPELINE_DB_ADMIN_USERNAME PIPELINE_DB_ADMIN_PASSWORD
  cd "${project_root}/frontend"
  export PATH="${node_bin_dir}:${PATH}"
  export FRONTEND_PORT="${frontend_port}"
  export DEV_API_TARGET="http://127.0.0.1:${backend_port}"
  exec "${node_bin}" "${project_root}/frontend/node_modules/@vue/cli-service/bin/vue-cli-service.js" serve --host 127.0.0.1
) >"${frontend_log}" 2>&1 &
frontend_pid=$!
wait_for_service "前端" "http://127.0.0.1:${frontend_port}/" "${frontend_pid}" "${frontend_log}"

step "执行真实浏览器 E2E"
(
  unset PIPELINE_DB_ADMIN_USERNAME PIPELINE_DB_ADMIN_PASSWORD
  export E2E_BASE_URL="http://127.0.0.1:${frontend_port}"
  export E2E_CONFIRM_ISOLATED=YES
  export E2E_USERNAME="${bootstrap_username}"
  export E2E_PASSWORD="${bootstrap_password}"
  export E2E_RESIDENT_USERNAME="${resident_username}"
  export E2E_RESIDENT_PASSWORD="${resident_password}"
  export E2E_ARTIFACT_DIR="${artifact_dir}/e2e"
  if [[ -n "${PIPELINE_BROWSER_EXECUTABLE:-}" ]]; then
    export E2E_BROWSER_EXECUTABLE="${PIPELINE_BROWSER_EXECUTABLE}"
  fi
  exec "${uv_bin}" run --script "${project_root}/scripts/ui-e2e.py"
) 2>&1 | tee "${artifact_dir}/browser-e2e.log"

step "执行四条注册审核浏览器 E2E"
(
  unset PIPELINE_DB_ADMIN_USERNAME PIPELINE_DB_ADMIN_PASSWORD
  export E2E_BASE_URL="http://127.0.0.1:${frontend_port}"
  export E2E_CONFIRM_ISOLATED=YES
  export E2E_USERNAME="${bootstrap_username}"
  export E2E_PASSWORD="${bootstrap_password}"
  if [[ -n "${PIPELINE_BROWSER_EXECUTABLE:-}" ]]; then
    export E2E_BROWSER_EXECUTABLE="${PIPELINE_BROWSER_EXECUTABLE}"
  fi
  exec "${uv_bin}" run --script "${project_root}/scripts/registration-ui-e2e.py"
) 2>&1 | tee "${artifact_dir}/registration-ui-e2e.log"

registration_ids="$(python3 - "${artifact_dir}/registration-ui-e2e.log" <<'PY'
import json
import sys
from pathlib import Path

prefix = "REGISTRATION_E2E_RESULT "
lines = [line for line in Path(sys.argv[1]).read_text().splitlines() if line.startswith(prefix)]
if len(lines) != 1:
    raise SystemExit("registration E2E result marker is missing or duplicated")
result = json.loads(lines[0][len(prefix):])
if not result.get("ok") or result.get("applications") != 4 or result.get("reviewRequests") != 4:
    raise SystemExit("registration E2E result is incomplete")
keys = [
    "staffApprovedUserId",
    "staffRejectedUserId",
    "residentApprovedUserId",
    "residentRejectedUserId",
    "approvedResidentFixtureId",
    "rejectedResidentFixtureId",
]
print("\t".join(str(result[key]) for key in keys))
PY
)"
IFS=$'\t' read -r staff_approved_user_id staff_rejected_user_id \
  resident_approved_user_id resident_rejected_user_id \
  approved_resident_fixture_id rejected_resident_fixture_id <<<"${registration_ids}"
for registration_id in \
  "${staff_approved_user_id}" "${staff_rejected_user_id}" \
  "${resident_approved_user_id}" "${resident_rejected_user_id}" \
  "${approved_resident_fixture_id}" "${rejected_resident_fixture_id}"; do
  [[ "${registration_id}" =~ ^[1-9][0-9]*$ ]] || fail "注册审核 E2E 返回了非法资源 ID"
done

staff_approved_state="$(mysql_exec --database="${database_name}" --execute="
  SELECT CONCAT(u.approval_status, '|', u.status, '|', COUNT(ur.role_id), '|',
    COALESCE(MAX(r.role_code), ''))
  FROM sys_user u
  LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.ended_at IS NULL
  LEFT JOIN sys_role r ON r.id = ur.role_id
  WHERE u.id = ${staff_approved_user_id}
  GROUP BY u.id, u.approval_status, u.status;")"
[[ "${staff_approved_state}" == "APPROVED|ENABLED|1|COMMUNITY_STAFF" ]] ||
  fail "工作人员批准后的账号状态或角色不符合预期"

resident_approved_state="$(mysql_exec --database="${database_name}" --execute="
  SELECT CONCAT(u.approval_status, '|', u.status, '|', COUNT(ur.role_id), '|',
    COALESCE(MAX(role.role_code), ''), '|', COALESCE(MAX(resident.user_id), 0))
  FROM sys_user u
  LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.ended_at IS NULL
  LEFT JOIN sys_role role ON role.id = ur.role_id
  LEFT JOIN resident ON resident.id = ${approved_resident_fixture_id}
  WHERE u.id = ${resident_approved_user_id}
  GROUP BY u.id, u.approval_status, u.status;")"
[[ "${resident_approved_state}" == "APPROVED|ENABLED|1|RESIDENT|${resident_approved_user_id}" ]] ||
  fail "居民批准后的账号、角色或档案绑定不符合预期"

rejected_account_count="$(mysql_exec --database="${database_name}" --execute="
  SELECT COUNT(*)
  FROM sys_user u
  WHERE u.id IN (${staff_rejected_user_id}, ${resident_rejected_user_id})
    AND u.approval_status = 'REJECTED'
    AND u.status = 'DISABLED'
    AND NOT EXISTS (
      SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.ended_at IS NULL
    );")"
[[ "${rejected_account_count}" == "2" ]] || fail "驳回账号仍可用或获得了角色"

rejected_resident_binding="$(mysql_exec --database="${database_name}" --execute="
  SELECT COUNT(*) FROM resident
  WHERE id = ${rejected_resident_fixture_id} AND user_id IS NULL;")"
[[ "${rejected_resident_binding}" == "1" ]] || fail "被驳回居民申请错误绑定了居民档案"

step "核验居民敏感字段访问审计"
sensitive_search_audit_count="$(mysql_exec --database="${database_name}" --execute="SELECT COUNT(*) FROM resident_sensitive_access_log WHERE action = 'SEARCH' AND purpose IS NULL;")"
(( sensitive_search_audit_count >= 3 )) || fail "居民敏感字段检索审计应至少覆盖两条 API 与一条 UI 路径，实际为 ${sensitive_search_audit_count}"
sensitive_view_audit_count="$(mysql_exec --database="${database_name}" --execute="SELECT COUNT(*) FROM resident_sensitive_access_log WHERE action = 'VIEW' AND CHAR_LENGTH(TRIM(purpose)) BETWEEN 5 AND 200;")"
(( sensitive_view_audit_count >= 2 )) || fail "居民敏感字段查看审计应至少覆盖 API 与 UI 两条合规用途，实际为 ${sensitive_view_audit_count}"

step "扫描验证产物中的合成敏感明文"
SENSITIVE_ID_CARD="${synthetic_id_card}" SENSITIVE_PHONE="${synthetic_phone}" \
SENSITIVE_ALT_PHONE="137${synthetic_phone:3}" \
  python3 - "${artifact_dir}" <<'PY'
import os
import re
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1])
plain_values = [
    os.environ["SENSITIVE_ID_CARD"],
    os.environ["SENSITIVE_PHONE"],
    os.environ["SENSITIVE_ALT_PHONE"],
]
needles = [value.encode() for value in plain_values]
matches = []
ocr_matches = []
for path in root.rglob("*"):
    if not path.is_file():
        continue
    data = path.read_bytes()
    if any(needle in data for needle in needles):
        matches.append(str(path.relative_to(root)))
    normalized_text = re.sub(r"\D", "", data.decode("utf-8", errors="ignore"))
    if any(re.sub(r"\D", "", value) in normalized_text for value in plain_values):
        matches.append(str(path.relative_to(root)))
    if path.suffix.lower() == ".png":
        result = subprocess.run(
            ["tesseract", str(path), "stdout", "--psm", "6"],
            check=False,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            print(f"OCR failed for {path}: {result.stderr.strip()}", file=sys.stderr)
            raise SystemExit(1)
        normalized_ocr = re.sub(r"\D", "", result.stdout)
        if any(re.sub(r"\D", "", value) in normalized_ocr for value in plain_values):
            ocr_matches.append(str(path.relative_to(root)))
if matches:
    print("Sensitive plaintext detected in: " + ", ".join(matches), file=sys.stderr)
    raise SystemExit(1)
if ocr_matches:
    print("Sensitive plaintext detected by screenshot OCR in: " + ", ".join(ocr_matches), file=sys.stderr)
    raise SystemExit(1)
print("sensitive_artifact_matches=0 (raw bytes + screenshot OCR)")
PY

pipeline_completed=1
