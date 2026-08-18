#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
artifact_root="${THESIS_ARTIFACT_ROOT:-${TMPDIR:-/tmp}/community-governance-thesis}"
run_stamp="$(date -u +%Y%m%dT%H%M%SZ)-$$"
database_stamp="$(date -u +%Y%m%d%H%M%S)"
artifact_dir="${artifact_root%/}/${run_stamp}"
database_name="community_governance_thesis_${database_stamp}_$$"
backend_log="${artifact_dir}/backend.log"
frontend_log="${artifact_dir}/frontend.log"

db_host="${THESIS_DB_HOST:-127.0.0.1}"
db_port="${THESIS_DB_PORT:-3306}"
db_admin_username="${THESIS_DB_ADMIN_USERNAME:-root}"
db_admin_password="${THESIS_DB_ADMIN_PASSWORD:-}"
allow_remote_db="${THESIS_ALLOW_REMOTE_DB:-0}"

backend_pid=""
frontend_pid=""
database_created=0
figures_completed=0

fail() {
  printf '[thesis] ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令：$1"
}

node_major() {
  "$1" -p 'process.versions.node.split(".")[0]' 2>/dev/null
}

resolve_node_22() {
  local candidate
  local -a candidates=()
  [[ -n "${THESIS_NODE_BIN:-}" ]] && candidates+=("${THESIS_NODE_BIN}")
  command -v node >/dev/null 2>&1 && candidates+=("$(command -v node)")
  candidates+=("/opt/homebrew/opt/node@22/bin/node" "/usr/local/opt/node@22/bin/node")
  for candidate in "${candidates[@]}"; do
    if [[ -x "$candidate" ]] && [[ "$(node_major "$candidate")" == 22 ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done
  fail "未找到 Node 22；请设置 THESIS_NODE_BIN"
}

resolve_java_home() {
  local candidate="${THESIS_JAVA_HOME:-${JAVA_HOME:-}}"
  if [[ -n "$candidate" && -x "$candidate/bin/java" ]] && "$candidate/bin/java" -version 2>&1 | head -n 1 | grep -Eq 'version "17([."]|$)'; then
    printf '%s\n' "$candidate"
    return
  fi
  if [[ -x /usr/libexec/java_home ]]; then
    candidate="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  fi
  fail "未找到 JDK 17；请设置 THESIS_JAVA_HOME"
}

find_free_port() {
  "$node_bin" -e '
    const net = require("node:net");
    const server = net.createServer();
    server.listen(0, "127.0.0.1", () => {
      console.log(server.address().port);
      server.close();
    });
  '
}

mysql_exec() {
  MYSQL_PWD="$db_admin_password" "$mysql_bin" --protocol=TCP \
    --host="$db_host" --port="$db_port" --user="$db_admin_username" \
    --batch --skip-column-names "$@"
}

stop_process() {
  local process_id="$1"
  [[ -n "$process_id" ]] || return
  kill -0 "$process_id" 2>/dev/null || return
  kill "$process_id" 2>/dev/null || true
  for _ in {1..30}; do
    if ! kill -0 "$process_id" 2>/dev/null; then
      wait "$process_id" 2>/dev/null || true
      return
    fi
    sleep 0.1
  done
  kill -KILL "$process_id" 2>/dev/null || true
  wait "$process_id" 2>/dev/null || true
}

wait_for_service() {
  local label="$1"
  local url="$2"
  local process_id="$3"
  local log_file="$4"
  for _ in {1..120}; do
    curl --silent --show-error --fail "$url" >/dev/null 2>&1 && return
    if ! kill -0 "$process_id" 2>/dev/null; then
      tail -n 80 "$log_file" >&2 || true
      fail "$label 启动失败"
    fi
    sleep 0.5
  done
  tail -n 80 "$log_file" >&2 || true
  fail "$label 在 60 秒内未就绪"
}

cleanup() {
  local exit_code=$?
  local database_disposition="未创建"
  trap - EXIT INT TERM
  set +e
  stop_process "$frontend_pid"
  stop_process "$backend_pid"
  if [[ "$database_created" == 1 ]]; then
    if [[ "$database_name" =~ ^community_governance_thesis_[0-9_]+$ ]] && mysql_exec --execute="DROP DATABASE \`$database_name\`;" >/dev/null; then
      database_disposition="已删除：$database_name"
    else
      database_disposition="清理失败：$database_name"
      exit_code=1
    fi
  fi
  if [[ "$exit_code" == 0 && "$figures_completed" == 1 ]]; then
    printf '\nTHESIS FIGURES PASS\n'
  else
    printf '\nTHESIS FIGURES FAIL\n' >&2
  fi
  printf 'screenshots=%s\n' "$project_root/docs/thesis/screenshots"
  printf 'artifacts=%s\n' "$artifact_dir"
  printf 'database=%s\n' "$database_disposition"
  exit "$exit_code"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

[[ "$allow_remote_db" == 0 || "$allow_remote_db" == 1 ]] || fail "THESIS_ALLOW_REMOTE_DB 只能为 0 或 1"
[[ "$db_port" =~ ^[0-9]+$ ]] && ((db_port >= 1024 && db_port <= 65535)) || fail "THESIS_DB_PORT 必须是 1024-65535 的端口"
if [[ "$db_host" != 127.0.0.1 && "$db_host" != localhost && "$db_host" != ::1 && "$allow_remote_db" != 1 ]]; then
  fail "默认只允许本机数据库；隔离的远程测试库需显式设置 THESIS_ALLOW_REMOTE_DB=1"
fi

for command_name in curl grep head mysql openssl tail tr uv; do
  require_command "$command_name"
done

node_bin="$(resolve_node_22)"
java_home="$(resolve_java_home)"
java_bin="$java_home/bin/java"
mysql_bin="$(command -v mysql)"
uv_bin="$(command -v uv)"
backend_jar="$project_root/backend/target/community-governance-backend-0.1.0-SNAPSHOT.jar"
frontend_cli="$project_root/frontend/node_modules/@vue/cli-service/bin/vue-cli-service.js"
[[ -f "$backend_jar" ]] || fail "缺少后端 JAR；请先运行 scripts/validation-pipeline.sh"
[[ -f "$frontend_cli" ]] || fail "缺少前端依赖；请先运行 scripts/validation-pipeline.sh 或 npm ci"

backend_port="${THESIS_BACKEND_PORT:-$(find_free_port)}"
frontend_port="${THESIS_FRONTEND_PORT:-$(find_free_port)}"
[[ "$backend_port" != "$frontend_port" ]] || fail "前后端端口不得相同"
mkdir -p "$artifact_dir/attachments"

admin_username="thesis-admin-${database_stamp}-$$"
admin_password="$(openssl rand -base64 36 | tr -d '\r\n')Aa1!"
data_encryption_key="$(openssl rand -base64 32 | tr -d '\r\n')"

printf '[thesis] 创建隔离数据库\n'
mysql_exec --execute="CREATE DATABASE \`$database_name\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
database_created=1

printf '[thesis] 启动隔离后端并装载四组演示数据\n'
(
  export SERVER_PORT="$backend_port"
  export DB_URL="jdbc:mysql://${db_host}:${db_port}/${database_name}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
  export DB_USERNAME="$db_admin_username" DB_PASSWORD="$db_admin_password"
  export DB_POOL_MIN_IDLE=1 DB_POOL_MAX_SIZE=4
  export FRONTEND_ORIGIN="http://127.0.0.1:$frontend_port"
  export DATA_ENCRYPTION_KEY="$data_encryption_key"
  export ATTACHMENT_STORAGE_ROOT="$artifact_dir/attachments"
  export BOOTSTRAP_ADMIN_ENABLED=true BOOTSTRAP_ADMIN_USERNAME="$admin_username"
  export BOOTSTRAP_ADMIN_PASSWORD="$admin_password" BOOTSTRAP_ADMIN_REAL_NAME="论文演示管理员"
  exec "$java_bin" -jar "$backend_jar"
) >"$backend_log" 2>&1 &
backend_pid=$!
wait_for_service "后端" "http://127.0.0.1:$backend_port/api/auth/csrf" "$backend_pid" "$backend_log"

DEMO_CONFIRM_ISOLATED=YES \
SMOKE_CONFIRM_ISOLATED=YES \
DEMO_BASE_URL="http://127.0.0.1:$backend_port" \
DEMO_ADMIN_USERNAME="$admin_username" \
DEMO_ADMIN_PASSWORD="$admin_password" \
"$node_bin" "$project_root/scripts/seed-demo.mjs"

printf '[thesis] 启动隔离前端并拍摄 8 张图片\n'
(
  cd "$project_root/frontend"
  export PATH="$(dirname "$node_bin"):$PATH"
  export FRONTEND_PORT="$frontend_port" DEV_API_TARGET="http://127.0.0.1:$backend_port"
  exec "$node_bin" "$frontend_cli" serve --host 127.0.0.1
) >"$frontend_log" 2>&1 &
frontend_pid=$!
wait_for_service "前端" "http://127.0.0.1:$frontend_port/" "$frontend_pid" "$frontend_log"

THESIS_BASE_URL="http://127.0.0.1:$frontend_port" \
THESIS_USERNAME="$admin_username" \
THESIS_PASSWORD="$admin_password" \
"$uv_bin" run --script "$project_root/scripts/thesis-screenshots.py"

[[ "$(find "$project_root/docs/thesis/screenshots" -maxdepth 1 -name '*.png' | wc -l | tr -d ' ')" == 8 ]] \
  || fail "截图目录未生成预期的 8 张 PNG"
figures_completed=1
