#!/usr/bin/env bash

set -Eeuo pipefail

base_url="${SMOKE_BASE_URL:-http://127.0.0.1:18080}"
isolated_confirmation="${SMOKE_CONFIRM_ISOLATED:-}"
allow_remote_target="${SMOKE_ALLOW_REMOTE_TARGET:-}"
confirm_remote_disposable="${SMOKE_CONFIRM_REMOTE_DISPOSABLE:-}"
allowed_remote_host="${SMOKE_ALLOWED_REMOTE_HOST:-}"
admin_username="${SMOKE_ADMIN_USERNAME:-}"
admin_password="${SMOKE_ADMIN_PASSWORD:-}"
category_id="${SMOKE_CATEGORY_ID:-1}"
run_id="${SMOKE_RUN_ID:-$(date +%s)}"
worker_username="smoke_worker_${run_id}"
reviewer_username="smoke_reviewer_${run_id}"
worker_password="${SMOKE_USER_PASSWORD:-}"
work_dir=""
admin_cookie=""
worker_cookie=""
reviewer_cookie=""

cleanup() {
  [[ -n "${work_dir}" && -d "${work_dir}" ]] || return
  find "${work_dir}" -type f -delete 2>/dev/null || true
  rmdir "${work_dir}" 2>/dev/null || true
}
trap cleanup EXIT

validate_password() {
  local name="$1"
  local value="$2"
  if ((${#value} < 12 || ${#value} > 128)) ||
    [[ ! "${value}" =~ [a-z] ]] || [[ ! "${value}" =~ [A-Z] ]] ||
    [[ ! "${value}" =~ [0-9] ]] || [[ "${value}" =~ ^[A-Za-z0-9]+$ ]]; then
    printf '%s 必须为 12-128 位，并包含大写、小写、数字和特殊字符。\n' "${name}" >&2
    exit 1
  fi
}

validate_target() {
  local target_host=""
  if [[ "${base_url}" =~ ^https?://(\[[^]]+\]|[^/:?#]+)(:[0-9]+)?/?$ ]]; then
    target_host="${BASH_REMATCH[1]}"
  else
    printf 'SMOKE_BASE_URL 必须是无路径、查询或片段的 HTTP(S) 绝对地址。\n' >&2
    exit 1
  fi

  if [[ "${target_host}" == "127.0.0.1" || "${target_host}" == "localhost" || "${target_host}" == "[::1]" ]]; then
    return
  fi
  local normalized_target_host="${target_host#[}"
  normalized_target_host="${normalized_target_host%]}"
  local normalized_allowed_host="${allowed_remote_host#[}"
  normalized_allowed_host="${normalized_allowed_host%]}"
  if [[ "${allow_remote_target}" != "YES" || "${confirm_remote_disposable}" != "YES" ||
    "${normalized_allowed_host}" != "${normalized_target_host}" ]]; then
    printf '远程 SMOKE_BASE_URL 必须由 SMOKE_ALLOWED_REMOTE_HOST 精确匹配，并同时设置 SMOKE_ALLOW_REMOTE_TARGET=YES 和 SMOKE_CONFIRM_REMOTE_DISPOSABLE=YES。\n' >&2
    exit 1
  fi
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf '缺少命令：%s\n' "$1" >&2
    exit 1
  fi
}

require_command curl
require_command jq
require_command awk

if [[ "${isolated_confirmation}" != "YES" ]]; then
  printf 'SMOKE_CONFIRM_ISOLATED 必须精确设置为 YES，且必须在任何请求前校验。\n' >&2
  exit 1
fi
validate_target
if [[ -z "${admin_username}" || -z "${admin_password}" || -z "${worker_password}" ]]; then
  printf '必须设置 SMOKE_ADMIN_USERNAME、SMOKE_ADMIN_PASSWORD 和 SMOKE_USER_PASSWORD。\n' >&2
  exit 1
fi
validate_password SMOKE_ADMIN_PASSWORD "${admin_password}"
validate_password SMOKE_USER_PASSWORD "${worker_password}"

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/community-governance-smoke.XXXXXX")"
admin_cookie="${work_dir}/admin.cookies"
worker_cookie="${work_dir}/worker.cookies"
reviewer_cookie="${work_dir}/reviewer.cookies"

csrf_cookie() {
  local cookie_file="$1"
  curl --silent --show-error --fail-with-body \
    --cookie "${cookie_file}" \
    --cookie-jar "${cookie_file}" \
    "${base_url}/api/auth/csrf" >/dev/null
  awk '$6 == "XSRF-TOKEN" { value = $7 } END { print value }' "${cookie_file}"
}

request_json() {
  local method="$1"
  local cookie_file="$2"
  local path="$3"
  local payload="${4:-}"
  local csrf_token
  local -a arguments=(
    --silent
    --show-error
    --fail-with-body
    --request "${method}"
    --cookie "${cookie_file}"
    --cookie-jar "${cookie_file}"
  )

  if [[ "${method}" != "GET" ]]; then
    csrf_token="$(csrf_cookie "${cookie_file}")"
    arguments+=(
      --header "Content-Type: application/json"
      --header "X-XSRF-TOKEN: ${csrf_token}"
      --data-binary @-
    )
    curl "${arguments[@]}" "${base_url}${path}" <<<"${payload}"
    return
  fi

  curl "${arguments[@]}" "${base_url}${path}"
}

expect() {
  local response="$1"
  local expression="$2"
  local description="$3"
  if ! jq -e "${expression}" >/dev/null <<<"${response}"; then
    printf '断言失败：%s\n' "${description}" >&2
    jq . <<<"${response}" >&2
    exit 1
  fi
}

login() {
  local cookie_file="$1"
  local username="$2"
  local password="$3"
  local payload
  local response

  csrf_cookie "${cookie_file}" >/dev/null
  payload="$(jq -cn --arg username "${username}" --arg password "${password}" \
    '{username: $username, password: $password}')"
  response="$(request_json POST "${cookie_file}" "/api/auth/login" "${payload}")"
  expect "${response}" '.code == "OK"' "用户 ${username} 登录"

  # 登录会轮换 Session/CSRF；后续写操作前重新生成 Cookie token。
  csrf_cookie "${cookie_file}" >/dev/null
}

printf '[1/9] 登录引导管理员并检查角色目录\n'
login "${admin_cookie}" "${admin_username}" "${admin_password}"
roles_response="$(request_json GET "${admin_cookie}" "/api/system/roles")"
expect "${roles_response}" \
  '(.data | map(.code) | index("SYSTEM_ADMIN")) != null and
   (.data | map(.code) | index("GRID_WORKER")) != null' \
  '系统角色目录'

printf '[2/9] 创建网格员和独立复核管理员\n'
worker_payload="$(jq -cn \
  --arg username "${worker_username}" \
  --arg password "${worker_password}" \
  '{username: $username, password: $password, realName: "冒烟网格员",
    roleCodes: ["GRID_WORKER"]}')"
worker_response="$(request_json POST "${admin_cookie}" "/api/system/users" "${worker_payload}")"
expect "${worker_response}" '.code == "OK" and .data.roles == ["GRID_WORKER"]' '创建网格员'
worker_id="$(jq -r '.data.id' <<<"${worker_response}")"

reviewer_payload="$(jq -cn \
  --arg username "${reviewer_username}" \
  --arg password "${worker_password}" \
  '{username: $username, password: $password, realName: "冒烟复核管理员",
    roleCodes: ["COMMUNITY_STAFF"]}')"
reviewer_response="$(request_json POST "${admin_cookie}" "/api/system/users" "${reviewer_payload}")"
expect "${reviewer_response}" '.code == "OK" and .data.roles == ["COMMUNITY_STAFF"]' '创建待授权复核管理员'
reviewer_id="$(jq -r '.data.id' <<<"${reviewer_response}")"
reviewer_version="$(jq -r '.data.version' <<<"${reviewer_response}")"
reviewer_roles_payload="$(jq -cn --argjson version "${reviewer_version}" \
  '{version: $version, roleCodes: ["SYSTEM_ADMIN"]}')"
reviewer_response="$(request_json PUT "${admin_cookie}" \
  "/api/system/users/${reviewer_id}/roles" "${reviewer_roles_payload}")"
expect "${reviewer_response}" '.code == "OK" and .data.roles == ["SYSTEM_ADMIN"]' '替换复核管理员角色'

printf '[3/9] 创建社区、网格并分配责任网格员\n'
community_payload="$(jq -cn \
  --arg name "冒烟社区-${run_id}" \
  '{areaType: "COMMUNITY", areaName: $name, address: "冒烟验证地址"}')"
community_response="$(request_json POST "${admin_cookie}" "/api/grids" "${community_payload}")"
expect "${community_response}" '.code == "OK" and .data.areaType == "COMMUNITY"' '创建社区'
community_id="$(jq -r '.data.id' <<<"${community_response}")"

grid_payload="$(jq -cn \
  --arg communityId "${community_id}" \
  --arg name "冒烟网格-${run_id}" \
  '{areaType: "GRID", communityId: $communityId, areaName: $name,
    address: "冒烟社区一号网格"}')"
grid_response="$(request_json POST "${admin_cookie}" "/api/grids" "${grid_payload}")"
expect "${grid_response}" '.code == "OK" and .data.areaType == "GRID"' '创建网格'
grid_id="$(jq -r '.data.id' <<<"${grid_response}")"
grid_version="$(jq -r '.data.version' <<<"${grid_response}")"

assignment_payload="$(jq -cn \
  --arg workerId "${worker_id}" \
  --argjson version "${grid_version}" \
  '{version: $version, assignments: [{userId: $workerId, isPrimary: true}]}')"
grid_response="$(request_json PUT "${admin_cookie}" \
  "/api/grids/${grid_id}/assignments" "${assignment_payload}")"
expect "${grid_response}" '.code == "OK" and (.data.assignments | length) == 1' '分配网格员'

community_list="$(request_json GET "${admin_cookie}" \
  "/api/grids?areaType=COMMUNITY&page=1&size=100")"
expect "${community_list}" \
  ".code == \"OK\" and (.data.items | map(.id) | index(\"${community_id}\")) != null" \
  '新增社区可从社区列表读取'

printf '[4/9] 创建家庭户和含加密字段的居民\n'
household_payload="$(jq -cn \
  --arg gridId "${grid_id}" \
  '{gridId: $gridId, buildingNo: "1栋", unitNo: "1单元", roomNo: "101",
    address: "冒烟社区1栋1单元101"}')"
household_response="$(request_json POST "${admin_cookie}" "/api/households" "${household_payload}")"
expect "${household_response}" '.code == "OK" and .data.status == "ACTIVE"' '创建家庭户'
household_id="$(jq -r '.data.id' <<<"${household_response}")"

id_suffix="$(printf '%04d' "$((run_id % 10000))")"
phone_suffix="$(printf '%08d' "$((run_id % 100000000))")"
id_card="11010119900101${id_suffix}"
phone="138${phone_suffix}"
resident_payload="$(jq -cn \
  --arg gridId "${grid_id}" \
  --arg householdId "${household_id}" \
  --arg idCard "${id_card}" \
  --arg phone "${phone}" \
  '{gridId: $gridId, householdId: $householdId, realName: "冒烟居民",
    gender: "MALE", birthDate: "1990-01-01", idCard: $idCard, phone: $phone,
    address: "冒烟社区1栋1单元101", isHouseholder: true,
    specialGroupTags: ["冒烟验证"], remark: "运行时闭环验证"}')"
resident_response="$(request_json POST "${admin_cookie}" "/api/residents" "${resident_payload}")"
expect "${resident_response}" \
  '.code == "OK" and .data.status == "ACTIVE" and
   (.data.idCardMasked | endswith("'"${id_suffix}"'")) and
   (.data.phoneMasked | endswith("'"${phone_suffix:4:4}"'"))' \
  '创建居民并返回脱敏字段'
resident_id="$(jq -r '.data.id' <<<"${resident_response}")"

household_list="$(request_json GET "${admin_cookie}" \
  "/api/households?gridId=${grid_id}&status=ACTIVE&page=1&size=100")"
expect "${household_list}" \
  ".code == \"OK\" and (.data.items | map(.id) | index(\"${household_id}\")) != null" \
  '家庭户 gridId/status 筛选'

printf '[5/9] 创建并取消独立巡查任务\n'
independent_task_payload="$(jq -cn \
  --arg gridId "${grid_id}" \
  --arg workerId "${worker_id}" \
  --arg title "独立巡查-${run_id}" \
  '{gridId: $gridId, taskType: "ROUTINE_INSPECTION", title: $title,
    description: "验证独立任务创建与取消", priority: "MEDIUM",
    assigneeUserId: $workerId}')"
independent_task_response="$(request_json POST "${admin_cookie}" \
  "/api/tasks" "${independent_task_payload}")"
expect "${independent_task_response}" \
  '.code == "OK" and .data.status == "PENDING_ACCEPT" and .data.sourceEventId == null' \
  '创建独立任务'
independent_task_id="$(jq -r '.data.id' <<<"${independent_task_response}")"
independent_task_version="$(jq -r '.data.version' <<<"${independent_task_response}")"
independent_cancel_payload="$(jq -cn --argjson version "${independent_task_version}" \
  '{version: $version, reason: "冒烟验证取消"}')"
independent_task_response="$(request_json POST "${admin_cookie}" \
  "/api/tasks/${independent_task_id}/cancel" "${independent_cancel_payload}")"
expect "${independent_task_response}" \
  '.code == "OK" and .data.status == "CANCELLED"' \
  '取消独立任务'

printf '[6/9] 上报、受理并派发事件\n'
event_payload="$(jq -cn \
  --arg categoryId "${category_id}" \
  --arg gridId "${grid_id}" \
  --arg title "冒烟事件-${run_id}" \
  '{categoryId: $categoryId, gridId: $gridId, title: $title,
    description: "用于验证事件到任务办结闭环", reportChannel: "WEB",
    severity: "HIGH", address: "冒烟社区中心", reporterName: "冒烟上报人"}')"
event_response="$(request_json POST "${admin_cookie}" "/api/events" "${event_payload}")"
expect "${event_response}" '.code == "OK" and .data.status == "REPORTED"' '上报事件'
event_id="$(jq -r '.data.id' <<<"${event_response}")"
event_version="$(jq -r '.data.version' <<<"${event_response}")"

accept_payload="$(jq -cn --argjson version "${event_version}" \
  '{version: $version, remark: "冒烟受理通过"}')"
event_response="$(request_json POST "${admin_cookie}" \
  "/api/events/${event_id}/accept" "${accept_payload}")"
expect "${event_response}" '.code == "OK" and .data.status == "ACCEPTED"' '受理事件'
event_version="$(jq -r '.data.version' <<<"${event_response}")"

dispatch_payload="$(jq -cn \
  --arg workerId "${worker_id}" \
  --argjson version "${event_version}" \
  '{version: $version, assigneeUserId: $workerId, taskTitle: "处置冒烟事件",
    taskDescription: "完成现场处置并提交复核", priority: "HIGH",
    remark: "派发至责任网格员"}')"
event_response="$(request_json POST "${admin_cookie}" \
  "/api/events/${event_id}/assign" "${dispatch_payload}")"
expect "${event_response}" '.code == "OK" and .data.status == "ASSIGNED"' '派发事件'
if [[ "$(jq -r '.data.assignedToUserId' <<<"${event_response}")" != "${worker_id}" ]]; then
  printf '断言失败：事件执行人不匹配\n' >&2
  exit 1
fi

event_flows="$(request_json GET "${admin_cookie}" "/api/events/${event_id}/flows")"
task_id="$(jq -r '.data[] | select(.action == "ASSIGN" and .taskId != null) | .taskId' \
  <<<"${event_flows}" | tail -n 1)"
if [[ -z "${task_id}" || "${task_id}" == "null" ]]; then
  printf '断言失败：事件派发流中没有任务 ID\n' >&2
  jq . <<<"${event_flows}" >&2
  exit 1
fi

printf '[7/9] 网格员登录、接单并提交复核\n'
login "${worker_cookie}" "${worker_username}" "${worker_password}"
task_response="$(request_json GET "${worker_cookie}" "/api/tasks/${task_id}")"
expect "${task_response}" '.code == "OK" and .data.status == "PENDING_ACCEPT"' '读取待接单任务'
task_version="$(jq -r '.data.version' <<<"${task_response}")"

task_accept_payload="$(jq -cn --argjson version "${task_version}" \
  '{version: $version, remark: "网格员已接单"}')"
task_response="$(request_json POST "${worker_cookie}" \
  "/api/tasks/${task_id}/accept" "${task_accept_payload}")"
expect "${task_response}" '.code == "OK" and .data.status == "PROCESSING"' '网格员接单'
task_version="$(jq -r '.data.version' <<<"${task_response}")"

submit_payload="$(jq -cn --argjson version "${task_version}" \
  '{version: $version, handlingResult: "已完成现场处置，问题恢复正常",
    attachmentIds: [], remark: "提交管理员复核"}')"
task_response="$(request_json POST "${worker_cookie}" \
  "/api/tasks/${task_id}/submit-review" "${submit_payload}")"
expect "${task_response}" \
  '.code == "OK" and .data.status == "PENDING_REVIEW" and
   .data.handlingResult == "已完成现场处置，问题恢复正常"' \
  '网格员提交复核'

printf '[8/9] 独立复核管理员通过任务并关闭事件\n'
login "${reviewer_cookie}" "${reviewer_username}" "${worker_password}"
task_response="$(request_json GET "${reviewer_cookie}" "/api/tasks/${task_id}")"
event_response="$(request_json GET "${reviewer_cookie}" "/api/events/${event_id}")"
task_version="$(jq -r '.data.version' <<<"${task_response}")"
event_version="$(jq -r '.data.version' <<<"${event_response}")"

review_payload="$(jq -cn \
  --argjson version "${task_version}" \
  --argjson eventVersion "${event_version}" \
  '{version: $version, eventVersion: $eventVersion, approved: true,
    remark: "复核通过，确认办结"}')"
task_response="$(request_json POST "${reviewer_cookie}" \
  "/api/tasks/${task_id}/review" "${review_payload}")"
expect "${task_response}" '.code == "OK" and .data.status == "COMPLETED"' '复核通过'

event_response="$(request_json GET "${reviewer_cookie}" "/api/events/${event_id}")"
expect "${event_response}" \
  '.code == "OK" and .data.status == "CLOSED" and
   .data.resultSummary == "已完成现场处置，问题恢复正常"' \
  '事件闭环'

printf '[9/9] 校验事件/任务完整流转记录\n'
event_flows="$(request_json GET "${reviewer_cookie}" "/api/events/${event_id}/flows")"
task_flows="$(request_json GET "${reviewer_cookie}" "/api/tasks/${task_id}/flows")"
expect "${event_flows}" \
  '([.data[].action] | contains(["REPORT", "ACCEPT", "ASSIGN", "START",
    "SUBMIT_REVIEW", "APPROVE"]))' \
  '事件流转动作'
expect "${task_flows}" \
  '([.data[].action] | contains(["ASSIGN", "ACCEPT", "SUBMIT_REVIEW", "APPROVE"]))' \
  '任务流转动作'

jq -n \
  --arg result "PASS" \
  --arg communityId "${community_id}" \
  --arg gridId "${grid_id}" \
  --arg workerId "${worker_id}" \
  --arg householdId "${household_id}" \
  --arg residentId "${resident_id}" \
  --arg eventId "${event_id}" \
  --arg taskId "${task_id}" \
  --arg independentTaskId "${independent_task_id}" \
  --arg eventStatus "$(jq -r '.data.status' <<<"${event_response}")" \
  --arg taskStatus "$(jq -r '.data.status' <<<"${task_response}")" \
  --argjson eventFlowCount "$(jq '.data | length' <<<"${event_flows}")" \
  --argjson taskFlowCount "$(jq '.data | length' <<<"${task_flows}")" \
  '{
    result: $result,
    ids: {
      community: $communityId,
      grid: $gridId,
      worker: $workerId,
      household: $householdId,
      resident: $residentId,
      event: $eventId,
      task: $taskId,
      independentTask: $independentTaskId
    },
    finalState: {
      event: $eventStatus,
      task: $taskStatus
    },
    flowCount: {
      event: $eventFlowCount,
      task: $taskFlowCount
    }
  }'
