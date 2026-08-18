#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.13,<3.14"
# dependencies = [
#   "greenlet==3.2.4",
#   "playwright==1.61.0",
# ]
# ///

"""Stateful isolated browser regression for the community governance UI."""

from __future__ import annotations

import base64
import json
import os
import sys
import tempfile
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit, urlunsplit

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import Page, Route, sync_playwright

DEFAULT_BASE_URL = "http://localhost:5173"
DEFAULT_CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
DEFAULT_TIMEOUT_MS = 15_000
TRANSPARENT_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
)


def required_env(name: str) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def normalize_base_url(raw_value: str) -> str:
    parsed = urlsplit(raw_value.strip())
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise RuntimeError("E2E_BASE_URL must be an absolute HTTP(S) URL")
    if parsed.query or parsed.fragment:
        raise RuntimeError("E2E_BASE_URL must not contain a query string or fragment")
    normalized_path = parsed.path.rstrip("/")
    return urlunsplit((parsed.scheme, parsed.netloc, normalized_path, "", ""))


def assert_isolated_target(base_url: str) -> None:
    hostname = (urlsplit(base_url).hostname or "").lower()
    if hostname in {"127.0.0.1", "localhost", "::1"}:
        return
    approved_host = os.environ.get("E2E_ALLOWED_REMOTE_HOST", "").strip().lower().strip("[]")
    if (
        os.environ.get("E2E_ALLOW_REMOTE_TARGET") != "YES"
        or os.environ.get("E2E_CONFIRM_REMOTE_DISPOSABLE") != "YES"
        or approved_host != hostname.strip("[]")
    ):
        raise RuntimeError(
            "Remote E2E_BASE_URL requires E2E_ALLOWED_REMOTE_HOST to exactly match "
            "the target, plus E2E_ALLOW_REMOTE_TARGET=YES and "
            "E2E_CONFIRM_REMOTE_DISPOSABLE=YES before any request"
        )


def origin(value: str) -> tuple[str, str, int]:
    parsed = urlsplit(value)
    normalized_scheme = {"ws": "http", "wss": "https"}.get(parsed.scheme.lower(), parsed.scheme.lower())
    default_port = 443 if normalized_scheme == "https" else 80
    return normalized_scheme, (parsed.hostname or "").lower(), parsed.port or default_port


def assert_same_origin_url(value: str, base_url: str) -> None:
    if origin(value) != origin(base_url):
        raise RuntimeError(f"Browser navigation escaped the approved E2E origin: {value}")


def install_network_guard(page: Page, base_url: str) -> Any:
    approved_origin = origin(base_url)
    live_map_tiles = os.environ.get("E2E_LIVE_MAP_TILES", "").strip() == "1"
    session = page.context.new_cdp_session(page)

    def guard(event: dict[str, Any]) -> None:
        request_id = event["requestId"]
        request = event["request"]
        request_url = request["url"]
        parsed = urlsplit(request_url)
        if parsed.scheme in {"about", "blob", "data"}:
            session.send("Fetch.continueRequest", {"requestId": request_id})
            return
        if origin(request_url) == approved_origin:
            session.send("Fetch.continueRequest", {"requestId": request_id})
            return
        if parsed.hostname == "tile.openstreetmap.org" and request["method"] == "GET":
            if live_map_tiles:
                session.send("Fetch.continueRequest", {"requestId": request_id})
            else:
                session.send(
                    "Fetch.fulfillRequest",
                    {
                        "requestId": request_id,
                        "responseCode": 200,
                        "responseHeaders": [{"name": "Content-Type", "value": "image/png"}],
                        "body": base64.b64encode(TRANSPARENT_PNG).decode("ascii"),
                    },
                )
            return
        session.send(
            "Fetch.failRequest",
            {"requestId": request_id, "errorReason": "BlockedByClient"},
        )

    session.on("Fetch.requestPaused", guard)
    session.send(
        "Fetch.enable",
        {"patterns": [{"urlPattern": "*", "requestStage": "Request"}]},
    )
    return session


def validate_password(name: str, value: str) -> None:
    if not 12 <= len(value) <= 128:
        raise RuntimeError(f"{name} must contain 12-128 characters")
    if not (
        any(character.islower() for character in value)
        and any(character.isupper() for character in value)
        and any(character.isdigit() for character in value)
        and any(not character.isalnum() for character in value)
    ):
        raise RuntimeError(
            f"{name} must contain upper-case, lower-case, numeric and special characters"
        )


def prepare_artifact_dir(raw_value: str) -> Path:
    candidate = Path(raw_value).expanduser()
    if not candidate.is_absolute():
        raise RuntimeError("E2E_ARTIFACT_DIR must be an absolute, dedicated run directory")
    if candidate.is_symlink():
        raise RuntimeError("E2E_ARTIFACT_DIR must not be a symbolic link")
    artifact_dir = candidate.resolve()
    project_root = Path(__file__).resolve().parent.parent
    forbidden_roots = {
        Path("/").resolve(),
        Path.home().resolve(),
        Path(tempfile.gettempdir()).resolve(),
        project_root,
    }
    if artifact_dir in forbidden_roots or artifact_dir.is_relative_to(project_root):
        raise RuntimeError("E2E_ARTIFACT_DIR must not target a broad or project directory")
    if artifact_dir.exists() and any(artifact_dir.iterdir()):
        raise RuntimeError("E2E_ARTIFACT_DIR must be new or empty for this run")
    artifact_dir.mkdir(parents=True, exist_ok=True)
    os.chmod(artifact_dir, 0o700)
    if artifact_dir.is_symlink() or not artifact_dir.is_dir():
        raise RuntimeError("E2E_ARTIFACT_DIR changed while it was being prepared")
    return artifact_dir


def browser_executable() -> str | None:
    configured = os.environ.get("E2E_BROWSER_EXECUTABLE", "").strip()
    if configured:
        executable = Path(configured).expanduser()
        if not executable.is_file():
            raise RuntimeError("E2E_BROWSER_EXECUTABLE does not point to a file")
        return str(executable)
    default_chrome = Path(DEFAULT_CHROME_PATH)
    return str(default_chrome) if default_chrome.is_file() else None


def present(value: Any) -> bool:
    return value is not None and str(value).strip() != ""


def check(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def progress(message: str) -> None:
    print(f"[ui-e2e] {message}", file=sys.stderr, flush=True)


def navigate(page: Page, url: str) -> None:
    page.goto(url, wait_until="domcontentloaded")
    assert_same_origin_url(page.url, url)
    page.wait_for_load_state("networkidle")


def is_expected_navigation_abort(request: Any, base_url: str) -> bool:
    """Ignore only same-origin read requests that Chromium cancels during route changes."""
    failure = str(request.failure or "")
    request_url = urlsplit(request.url)
    application_url = urlsplit(base_url)
    return (
        request.method == "GET"
        and "ERR_ABORTED" in failure
        and request_url.scheme == application_url.scheme
        and request_url.netloc == application_url.netloc
    )


def fetch_api_snapshot(page: Page) -> dict[str, Any]:
    return page.evaluate(
        """
        async () => {
          const paths = {
            grids: '/api/grids?page=1&size=20&areaType=GRID',
            residents: '/api/residents?page=1&size=20',
            households: '/api/households?page=1&size=20',
            events: '/api/events?page=1&size=20',
            tasks: '/api/tasks?page=1&size=20',
            topology: '/api/insights/grids'
          };
          const entries = await Promise.all(Object.entries(paths).map(async ([key, path]) => {
            const response = await fetch(path, { credentials: 'same-origin' });
            return [key, { status: response.status, body: await response.json() }];
          }));
          return Object.fromEntries(entries);
        }
        """
    )


def fetch_envelope(page: Page, path: str) -> dict[str, Any]:
    return page.evaluate(
        """
        async path => {
          const response = await fetch(path, { credentials: 'same-origin' });
          return { status: response.status, body: await response.json() };
        }
        """,
        path,
    )


def validate_navigation_snapshot(snapshot: dict[str, Any], expected_codes: list[str] | None = None) -> list[dict[str, Any]]:
    check(snapshot["status"] == 200, f"Navigation API returned HTTP {snapshot['status']}")
    check(snapshot["body"].get("code") == "OK", "Navigation API business response failed")
    items = snapshot["body"].get("data") or []
    check(isinstance(items, list), "Navigation data is not a list")
    required_fields = {"id", "code", "name", "routePath", "icon", "sortNo"}
    for item in items:
        check(required_fields.issubset(item), f"Navigation item misses required fields: {item}")
        check(str(item.get("routePath", "")).startswith("/"), "Navigation routePath is invalid")
    check(
        [item["sortNo"] for item in items] == sorted(item["sortNo"] for item in items),
        "Navigation items are not sorted by sortNo",
    )
    if expected_codes is not None:
        check([item["code"] for item in items] == expected_codes, "Navigation codes do not match the current role")
    return items


def validate_dashboard_snapshot(snapshot: dict[str, Any]) -> dict[str, Any]:
    check(snapshot["status"] == 200, f"Dashboard API returned HTTP {snapshot['status']}")
    check(snapshot["body"].get("code") == "OK", "Dashboard API business response failed")
    dashboard = snapshot["body"].get("data") or {}
    for key in ("gridEventStats", "categoryStats", "recentEvents"):
        check(isinstance(dashboard.get(key), list), f"Dashboard {key} is not a list")
    for item in dashboard["gridEventStats"]:
        check(
            {"gridId", "gridCode", "gridName", "eventCount", "completedWithDeadlineCount", "onTimeClosedCount", "onTimeCompletionRate"}.issubset(item),
            "Grid dashboard item misses D3 fields",
        )
        check(0 <= float(item["onTimeCompletionRate"]) <= 100, "D3 rate is not a 0..100 percentage")
    for item in dashboard["categoryStats"]:
        check(
            {"categoryId", "categoryName", "eventCount", "percentage"}.issubset(item),
            "Category dashboard item misses D4 fields",
        )
        check(0 <= float(item["percentage"]) <= 100, "D4 percentage is not a 0..100 percentage")
    check(len(dashboard["recentEvents"]) <= 10, "Dashboard recent events exceeds 10 rows")
    for item in dashboard["recentEvents"]:
        check(
            {"id", "eventNo", "title", "categoryName", "gridName", "status", "severity", "reportedAt"}.issubset(item),
            "Recent event misses D4 fields",
        )
    return dashboard


def validate_readable_api_fields(api_results: dict[str, Any]) -> dict[str, int]:
    for key, result in api_results.items():
        check(result["status"] == 200, f"{key} API returned HTTP {result['status']}")
        check(result["body"].get("code") == "OK", f"{key} API business response failed")

    def items(key: str) -> list[dict[str, Any]]:
        return api_results[key]["body"].get("data", {}).get("items", [])

    grids = items("grids")
    residents = items("residents")
    households = items("households")
    events = items("events")
    tasks = items("tasks")
    topology_data = api_results["topology"]["body"].get("data", {})
    communities = topology_data.get("communities") or topology_data.get("topology", {}).get("communities", [])

    check(grids and all(present(item.get("communityName")) for item in grids), "Grid community names are missing")
    check(residents and all(present(item.get("gridName")) for item in residents), "Resident grid names are missing")
    check(
        all(not present(item.get("householdId")) or present(item.get("householdNo")) for item in residents),
        "A resident household ID has no readable household number",
    )
    check(households and all(present(item.get("gridName")) for item in households), "Household grid names are missing")
    check(
        events and all(present(item.get("gridName")) and present(item.get("categoryName")) for item in events),
        "Event grid or category names are missing",
    )
    check(
        all(not present(item.get("assignedToUserId")) or present(item.get("assignedToName")) for item in events),
        "An assigned event has no readable assignee name",
    )
    check(tasks and all(present(item.get("gridName")) for item in tasks), "Task grid names are missing")
    check(
        all(not present(item.get("sourceEventId")) or present(item.get("sourceEventNo")) for item in tasks),
        "A task source event ID has no readable event number",
    )
    check(
        all(
            (not present(item.get("dispatcherUserId")) or present(item.get("dispatcherName")))
            and (not present(item.get("assigneeUserId")) or present(item.get("assigneeName")))
            for item in tasks
        ),
        "A task user ID has no readable person name",
    )
    check(communities and all(present(item.get("status")) for item in communities), "Community topology statuses are missing")

    return {
        "grids": len(grids),
        "residents": len(residents),
        "households": len(households),
        "events": len(events),
        "tasks": len(tasks),
        "communities": len(communities),
    }


def force_one_zero_event_breakdown(route: Route, marker: dict[str, bool]) -> None:
    response = route.fetch()
    payload = response.json()
    statuses = payload.get("data", {}).get("statuses", [])
    if statuses:
        statuses[0]["count"] = 0
        marker["applied"] = True
    route.fulfill(response=response, json=payload)


def run_suite(page: Page, base_url: str, username: str, password: str, artifact_dir: Path) -> dict[str, Any]:
    console_errors: list[str] = []
    page_errors: list[str] = []
    failed_requests: list[str] = []
    bad_api_responses: list[str] = []
    live_map_tiles = os.environ.get("E2E_LIVE_MAP_TILES", "").strip() == "1"

    page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
    page.on("pageerror", lambda error: page_errors.append(str(error)))
    def capture_failed_request(request: Any) -> None:
        failure = request.failure or ""
        if is_expected_navigation_abort(request, base_url):
            return
        if "tile.openstreetmap.org/" in request.url and "ERR_ABORTED" in failure:
            return
        failed_requests.append(f"{request.method} {request.url} {failure}".strip())

    page.on("requestfailed", capture_failed_request)

    def capture_bad_api(response: Any) -> None:
        if (
            "/api/" in response.url
            and response.status >= 400
            and not (response.status == 401 and "/api/auth/me" in response.url)
        ):
            bad_api_responses.append(f"{response.status} {response.url}")

    page.on("response", capture_bad_api)
    progress("logging in")
    with page.expect_response(lambda response: "/api/auth/csrf" in response.url and response.ok):
        navigate(page, f"{base_url}/login")
    page.locator('input[autocomplete="username"]').fill(username)
    page.locator('input[autocomplete="current-password"]').fill(password)
    with page.expect_response(
        lambda response: "/api/auth/login" in response.url and response.request.method == "POST"
    ) as login_info:
        page.get_by_role("button", name="进入工作台").click()
    login_response = login_info.value
    check(login_response.ok, f"Login failed with HTTP {login_response.status}")
    page.wait_for_url(f"{base_url}/dashboard")
    page.wait_for_load_state("networkidle")

    progress("checking root redirect, server navigation and dashboard")
    navigate(page, f"{base_url}/")
    page.wait_for_url(f"{base_url}/dashboard")
    admin_navigation = validate_navigation_snapshot(fetch_envelope(page, "/api/auth/navigation"))
    check(any(item["code"] == "DASHBOARD" for item in admin_navigation), "Administrator navigation misses dashboard")
    check(any(item["code"] == "EVENT_CATEGORY" for item in admin_navigation), "Administrator navigation misses event category")
    sidebar_labels = [text.strip() for text in page.locator(".app-menu .el-menu-item").all_inner_texts()]
    check(
        sidebar_labels == [item["name"] for item in admin_navigation],
        "Administrator sidebar does not exactly render the ordered server navigation",
    )
    dashboard = validate_dashboard_snapshot(fetch_envelope(page, "/api/dashboard/overview"))
    check(page.locator(".dashboard-analysis-panel").count() == 3, "Dashboard misses D3/D4 analysis panels")
    check(page.locator(".grid-quality-list, .dashboard-empty").count() >= 1, "Dashboard misses grid-event statistics view")
    check(page.locator(".category-distribution-list, .dashboard-empty").count() >= 1, "Dashboard misses category statistics view")
    check(page.locator(".recent-event-list, .dashboard-empty").count() >= 1, "Dashboard misses recent-events view")
    page.screenshot(path=str(artifact_dir / "dashboard-d3-d4.png"), full_page=True)

    progress("checking readable API fields")
    api_counts = validate_readable_api_fields(fetch_api_snapshot(page))

    progress("checking fixed-core access administration")
    navigate(page, f"{base_url}/system/roles")
    page.get_by_text("SYSTEM_ADMIN", exact=True).first.wait_for(state="visible")
    role_rows = page.locator(".resource-table .el-table__body-wrapper .el-table__row")
    fixed_role_count = role_rows.count()
    check(fixed_role_count == 4, "Role administration must show exactly four fixed core roles")
    check(
        page.get_by_role("button", name="配置权限", exact=True).count() >= 4,
        "Role administration has no permission configuration actions",
    )
    navigate(page, f"{base_url}/system/menus")
    page.locator('.query-bar input[placeholder="菜单编码、名称或权限码"]').fill("SYSTEM_ROLE")
    page.get_by_role("button", name="查询", exact=True).click()
    page.get_by_text("SYSTEM_ROLE", exact=True).first.wait_for(state="visible")
    check(page.get_by_text("system:role:manage", exact=True).count() >= 1, "Menu administration hides permission codes")
    page.screenshot(path=str(artifact_dir / "access-control.png"), full_page=True)

    access_snapshot = page.evaluate(
        """
        async () => {
          const listResponse = await fetch('/api/grids?page=1&size=20&areaType=COMMUNITY', { credentials: 'same-origin' });
          const listEnvelope = await listResponse.json();
          const community = listEnvelope.data?.items?.find(Boolean);
          if (!community) return null;
          const detailResponse = await fetch(`/api/grids/${community.id}`, { credentials: 'same-origin' });
          const detailEnvelope = await detailResponse.json();
          return detailEnvelope.code === 'OK' ? detailEnvelope.data : null;
        }
        """
    )
    check(access_snapshot is not None, "No community was available for assignment verification")
    check(access_snapshot.get("assignments"), "Community detail has no staff assignment")
    navigate(page, f"{base_url}/grids?view=list")
    area_select = page.locator(".query-bar .el-select").last
    area_select.click()
    community_option = page.locator(
        ".el-select-dropdown:visible .el-select-dropdown__item"
    ).filter(has_text="社区").last
    with page.expect_response(lambda response: "/api/grids?" in response.url and "areaType=COMMUNITY" in response.url):
        community_option.click()
    community_assignment_action = page.locator(
        ".resource-table .el-table__fixed-right .el-table__fixed-body-wrapper"
    ).get_by_role("button", name="分配社区人员", exact=True).first
    community_assignment_action.wait_for(state="visible")
    community_assignment_action.click()
    page.get_by_text("分配社区工作人员", exact=True).wait_for(state="visible")
    page.get_by_role("button", name="取消", exact=True).click()

    progress("checking desktop real-coordinate map")
    page.set_viewport_size({"width": 1440, "height": 1000})
    navigate(page, f"{base_url}/grids?view=map")
    records = page.locator(".spatial-grid-record")
    records.first.wait_for(state="visible")
    located_before = page.locator(".grid-leaflet-marker").count()
    unlocated_before = page.locator(".spatial-grid-record.is-unlocated").count()
    check(located_before > 0, "The real-coordinate map rendered no located grid marker")
    unlocated_group = page.locator(".map-record-group.is-unlocated-group")
    check(unlocated_group.count() == 1, "The map did not expose the unlocated-grid section")
    if unlocated_before == 0:
        check(
            page.locator(".all-located-note").count() == 1,
            "A fully located data set has no completion confirmation",
        )
    else:
        check(
            page.locator(".unlocated-guidance").count() == 1,
            "Unlocated grids have no coordinate guidance",
        )
    check(
        located_before + unlocated_before == records.count(),
        "The map record count does not match located plus unlocated grids",
    )
    check(page.locator(".pagination-row").count() == 0, "Spatial map still shows pagination")
    attribution = page.locator(".leaflet-control-attribution")
    check(attribution.count() == 1 and "OpenStreetMap" in attribution.inner_text(), "Map attribution is missing")
    marker = page.locator(".grid-leaflet-marker").first
    check(marker.get_attribute("tabindex") == "0", "Map marker is not keyboard focusable")
    check(present(marker.get_attribute("title")), "Map marker has no accessible name")
    grid_code = records.first.locator("small").inner_text().split(" · ", maxsplit=1)[0].strip()
    check(present(grid_code), "Could not read a grid code from the spatial map")
    page.locator('.query-bar input[placeholder="网格编码或名称"]').fill(grid_code)
    with page.expect_response(lambda response: "/api/grids?" in response.url and response.ok):
        page.get_by_role("button", name="查询", exact=True).click()
    page.locator(".spatial-grid-record").first.wait_for(state="visible")
    check(page.locator(".spatial-grid-record").count() == 1, "Map filter did not narrow to one grid")
    check(
        page.locator(".grid-leaflet-marker").count()
        + page.locator(".spatial-grid-record.is-unlocated").count()
        == 1,
        "Filtered map invented or duplicated a point",
    )
    check(
        "已定位" in page.locator(".map-truth-note").inner_text()
        and "待定位" in page.locator(".map-truth-note").inner_text(),
        "Map truth summary is missing",
    )
    check(page.locator(".pagination-row").count() == 0, "Pagination reappeared after filtering the map")
    page.screenshot(path=str(artifact_dir / "grid-map.png"), full_page=True)

    progress("checking mobile spatial map")
    page.set_viewport_size({"width": 390, "height": 844})
    navigate(page, f"{base_url}/grids?view=map")
    page.locator(".spatial-map-stage").wait_for(state="visible")
    check(
        page.evaluate("document.documentElement.scrollWidth <= window.innerWidth + 1"),
        "The mobile spatial map has document-level horizontal overflow",
    )
    map_box = page.locator(".spatial-map-stage").bounding_box()
    sidebar_box = page.locator(".spatial-map-sidebar").bounding_box()
    check(map_box is not None and map_box["width"] <= 390, "Mobile map exceeds the viewport width")
    check(
        map_box is not None and sidebar_box is not None and sidebar_box["y"] >= map_box["y"] + map_box["height"],
        "Mobile map sidebar did not stack below the map",
    )
    page.screenshot(path=str(artifact_dir / "mobile-grid-map.png"), full_page=True)

    progress("checking resident cards")
    page.set_viewport_size({"width": 1440, "height": 1000})
    navigate(page, f"{base_url}/residents?view=card")
    page.locator(".record-card").first.wait_for(state="visible")
    resident_grid_names = page.locator(".record-meta div").evaluate_all(
        """
        rows => rows
          .filter(row => row.querySelector('dt')?.textContent.trim() === '所属网格')
          .map(row => row.querySelector('dd')?.textContent.trim() || '')
        """
    )
    check(
        resident_grid_names and all(name and name != "—" for name in resident_grid_names),
        "Resident cards do not show readable grid names",
    )
    check("所属网格 ID" not in page.locator("body").inner_text(), "Resident cards still expose raw grid ID labels")
    page.screenshot(path=str(artifact_dir / "resident-cards.png"), full_page=True)

    progress("checking audited temporary resident sensitive view")
    first_resident_card = page.locator(".record-card").first
    first_resident_card.get_by_role("button", name="查看敏感字段", exact=True).click()
    sensitive_view_dialog = page.locator(".el-dialog:visible").filter(has_text="授权查看居民敏感字段")
    sensitive_view_dialog.get_by_placeholder("请填写具体业务用途，例如：办理居民养老补贴身份核验").fill(
        "浏览器回归验证居民身份资料"
    )
    with page.expect_response(
        lambda response: "/api/residents/" in response.url
        and response.url.endswith("/sensitive-view")
        and response.request.method == "POST"
    ) as sensitive_view_info:
        sensitive_view_dialog.get_by_role("button", name="确认并查看", exact=True).click()
    sensitive_view_response = sensitive_view_info.value
    check(sensitive_view_response.ok, f"Sensitive resident view returned HTTP {sensitive_view_response.status}")
    sensitive_view_envelope = sensitive_view_response.json()
    check(sensitive_view_envelope.get("code") == "OK", "Sensitive resident view business response failed")
    sensitive_view_data = sensitive_view_envelope.get("data") or {}
    full_id_card = sensitive_view_data.get("idCard")
    full_phone = sensitive_view_data.get("phone")
    check(present(full_id_card) and present(full_phone), "Sensitive resident view did not return complete test data")
    sensitive_view_dialog.get_by_text("敏感信息将在 60 秒后自动隐藏", exact=True).wait_for(state="visible")
    sensitive_dialog_text = sensitive_view_dialog.inner_text()
    check(str(full_id_card) in sensitive_dialog_text, "Sensitive view dialog did not render the ID card")
    check(str(full_phone) in sensitive_dialog_text, "Sensitive view dialog did not render the phone")
    sensitive_view_dialog.get_by_role("button", name="立即隐藏", exact=True).click()
    sensitive_view_dialog.wait_for(state="hidden")
    check(str(full_id_card) not in page.locator("body").inner_text(), "Closed dialog retained the full ID card in the DOM")
    check(str(full_phone) not in page.locator("body").inner_text(), "Closed dialog retained the full phone in the DOM")
    page.screenshot(path=str(artifact_dir / "resident-sensitive-view-hidden.png"), full_page=True)

    progress("checking masked resident sensitive exact search")
    # Element UI removes the dialog's accessibility mask after its leave transition.
    # Waiting for that transition keeps the page-header action in the accessibility tree.
    page.wait_for_timeout(500)
    sensitive_search_action = page.locator(".page-actions button").filter(has_text="敏感精确检索")
    sensitive_search_action.wait_for(state="visible")
    sensitive_search_action.click()
    sensitive_search_dialog = page.locator(".el-dialog:visible").filter(has_text="敏感字段精确检索")
    sensitive_search_dialog.get_by_placeholder("请输入完整号码，仅用于精确匹配").fill(str(full_phone))
    with page.expect_response(
        lambda response: response.url.endswith("/api/residents/sensitive-search")
        and response.request.method == "POST"
    ) as sensitive_search_info:
        sensitive_search_dialog.get_by_role("button", name="精确检索", exact=True).click()
    sensitive_search_response = sensitive_search_info.value
    check(sensitive_search_response.ok, f"Sensitive resident search returned HTTP {sensitive_search_response.status}")
    sensitive_search_dialog.get_by_text("检索结果", exact=True).wait_for(state="visible")
    search_dialog_text = sensitive_search_dialog.inner_text()
    check("共 1 条" in search_dialog_text, "Sensitive resident search did not return exactly one match")
    check(str(full_phone) not in search_dialog_text, "Sensitive resident search exposed the full phone")
    check(sensitive_search_dialog.get_by_role("button", name="授权查看", exact=True).count() == 1,
          "Sensitive resident search result has no authorized view action")
    sensitive_search_dialog.get_by_placeholder("请输入完整号码，仅用于精确匹配").fill("")
    check(str(full_phone) not in sensitive_search_dialog.inner_text(),
          "Sensitive resident search retained the full phone after input clearing")
    page.screenshot(path=str(artifact_dir / "resident-sensitive-search.png"), full_page=True)
    sensitive_search_dialog.get_by_role("button", name="取消", exact=True).click()
    sensitive_search_dialog.wait_for(state="hidden")
    page.wait_for_timeout(500)

    progress("checking sensitive access audit UI")
    sensitive_audit_action = page.locator(".page-actions button").filter(has_text="敏感访问审计")
    sensitive_audit_action.wait_for(state="visible")
    with page.expect_response(
        lambda response: "/api/residents/sensitive-access-logs" in response.url
        and response.request.method == "GET"
    ) as sensitive_audit_info:
        sensitive_audit_action.click()
    sensitive_audit_response = sensitive_audit_info.value
    check(sensitive_audit_response.ok, f"Sensitive access audit returned HTTP {sensitive_audit_response.status}")
    sensitive_audit_dialog = page.locator(".el-dialog:visible").filter(has_text="敏感信息访问审计")
    sensitive_audit_dialog.locator(".sensitive-audit-table .el-table").wait_for(state="visible")
    audit_dialog_text = sensitive_audit_dialog.inner_text()
    check("浏览器回归验证居民身份资料" in audit_dialog_text,
          "Sensitive access audit UI does not show the just-created purpose")
    check(str(full_id_card) not in audit_dialog_text and str(full_phone) not in audit_dialog_text,
          "Sensitive access audit UI exposed full ID card or phone")
    page.screenshot(path=str(artifact_dir / "resident-sensitive-audit.png"), full_page=True)
    sensitive_audit_dialog.locator(".el-dialog__headerbtn").click()
    sensitive_audit_dialog.wait_for(state="hidden")

    progress("checking authorized event attachment list and download")
    navigate(page, f"{base_url}/events?view=list")
    attachment_snapshot = page.evaluate(
        """
        async () => {
          const pageResponse = await fetch('/api/events?page=1&size=100', { credentials: 'same-origin' });
          const pageEnvelope = await pageResponse.json();
          for (const event of pageEnvelope.data?.items || []) {
            const response = await fetch(`/api/events/${event.id}/attachments`, { credentials: 'same-origin' });
            if (!response.ok) continue;
            const envelope = await response.json();
            if (envelope.code === 'OK' && Array.isArray(envelope.data) && envelope.data.length) {
              return { event, attachment: envelope.data[0] };
            }
          }
          return null;
        }
        """
    )
    check(attachment_snapshot is not None, "No event attachment was available for UI verification")
    event_number = attachment_snapshot["event"]["eventNo"]
    page.locator('.query-bar input[placeholder="事件编号、标题或地点"]').fill(event_number)
    with page.expect_response(lambda response: "/api/events?" in response.url and response.ok):
        page.get_by_role("button", name="查询", exact=True).click()
    # Element UI renders the fixed action column in a separate table, so the row
    # containing the event number does not itself contain the visible action button.
    event_row = page.locator(".resource-table .el-table__body-wrapper .el-table__row").filter(has_text=event_number).first
    event_row.wait_for(state="visible")
    attachment_action = page.locator(
        ".resource-table .el-table__fixed-right .el-table__fixed-body-wrapper"
    ).get_by_role("button", name="附件", exact=True).first
    attachment_action.wait_for(state="visible")
    attachment_action.click()
    page.locator(".attachment-item").first.wait_for(state="visible")
    check(
        attachment_snapshot["attachment"]["originalName"] in page.locator(".attachment-item").first.inner_text(),
        "Attachment dialog does not show the uploaded filename",
    )
    with page.expect_download() as download_info:
        page.locator(".attachment-item").first.get_by_role("button", name="下载", exact=True).click()
    download = download_info.value
    check(present(download.suggested_filename), "Attachment download has no filename")
    page.screenshot(path=str(artifact_dir / "event-attachments.png"), full_page=True)
    page.get_by_role("button", name="关闭", exact=True).click()

    progress("checking real event flow history")
    flow_snapshot = page.evaluate(
        """
        async () => {
          const listResponse = await fetch('/api/events?status=CLOSED&page=1&size=100', { credentials: 'same-origin' });
          const listEnvelope = await listResponse.json();
          for (const event of listEnvelope.data?.items || []) {
            const response = await fetch(`/api/events/${event.id}/flows`, { credentials: 'same-origin' });
            if (!response.ok) continue;
            const envelope = await response.json();
            const actions = (envelope.data || []).map(item => item.action);
            if (envelope.code === 'OK' && actions.includes('ACCEPT') && actions.includes('APPROVE')) {
              return { event, actions };
            }
          }
          return null;
        }
        """
    )
    check(flow_snapshot is not None, "No completed event flow was available for UI verification")
    navigate(page, f"{base_url}/events?view=list")
    page.locator('.query-bar input[placeholder="事件编号、标题或地点"]').fill(flow_snapshot["event"]["eventNo"])
    with page.expect_response(lambda response: "/api/events?" in response.url and response.ok):
        page.get_by_role("button", name="查询", exact=True).click()
    page.locator(".resource-table .el-table__body-wrapper .el-table__row").filter(
        has_text=flow_snapshot["event"]["eventNo"]
    ).first.wait_for(state="visible")
    flow_action = page.locator(
        ".resource-table .el-table__fixed-right .el-table__fixed-body-wrapper"
    ).get_by_role("button", name="流转", exact=True).first
    flow_action.click()
    flow_dialog = page.locator(".el-dialog:visible").filter(has=page.locator(".flow-history-list"))
    flow_dialog.locator(".flow-history-list").wait_for(state="visible")
    flow_text = flow_dialog.inner_text()
    check("受理事件" in flow_text and "复核通过" in flow_text, "Flow-history dialog does not render historical actions")
    page.screenshot(path=str(artifact_dir / "event-flow-history.png"), full_page=True)
    flow_dialog.get_by_role("button", name="关闭", exact=True).click()

    progress("checking task attachment list and authorized download")
    navigate(page, f"{base_url}/tasks?view=list")
    task_attachment_snapshot = page.evaluate(
        """
        async () => {
          const pageResponse = await fetch('/api/tasks?page=1&size=100', { credentials: 'same-origin' });
          const pageEnvelope = await pageResponse.json();
          for (const task of pageEnvelope.data?.items || []) {
            const response = await fetch(`/api/tasks/${task.id}/attachments`, { credentials: 'same-origin' });
            if (!response.ok) continue;
            const envelope = await response.json();
            if (envelope.code === 'OK' && Array.isArray(envelope.data) && envelope.data.length) {
              return { task, attachment: envelope.data[0] };
            }
          }
          return null;
        }
        """
    )
    check(task_attachment_snapshot is not None, "No task attachment was available for UI verification")
    task_number = task_attachment_snapshot["task"]["taskNo"]
    page.locator('.query-bar input[placeholder="任务编号或标题"]').fill(task_number)
    with page.expect_response(lambda response: "/api/tasks?" in response.url and response.ok):
        page.get_by_role("button", name="查询", exact=True).click()
    task_row = page.locator(".resource-table .el-table__body-wrapper .el-table__row").filter(has_text=task_number).first
    task_row.wait_for(state="visible")
    task_attachment_action = page.locator(
        ".resource-table .el-table__fixed-right .el-table__fixed-body-wrapper"
    ).get_by_role("button", name="附件", exact=True).first
    task_attachment_action.wait_for(state="visible")
    task_attachment_action.click()
    task_dialog = page.locator(".el-dialog:visible").filter(has_text="附件")
    task_dialog.locator(".attachment-item").first.wait_for(state="visible")
    check(
        task_attachment_snapshot["attachment"]["originalName"] in task_dialog.locator(".attachment-item").first.inner_text(),
        "Task attachment dialog does not show the uploaded filename",
    )
    with page.expect_download() as task_download_info:
        task_dialog.locator(".attachment-item").first.get_by_role("button", name="下载", exact=True).click()
    check(present(task_download_info.value.suggested_filename), "Task attachment download has no filename")
    page.screenshot(path=str(artifact_dir / "task-attachments.png"), full_page=True)
    task_dialog.get_by_role("button", name="关闭", exact=True).click()

    progress("checking mobile insight details and zero-value bars")
    zero_marker = {"applied": False}
    event_insight_pattern = "**/api/insights/events"
    route_handler = lambda route: force_one_zero_event_breakdown(route, zero_marker)
    page.route(event_insight_pattern, route_handler)
    page.set_viewport_size({"width": 390, "height": 844})
    navigate(page, f"{base_url}/events?view=card")
    check(zero_marker["applied"], "Could not create a deterministic zero-value event breakdown")
    details_toggle = page.locator(".insight-details-toggle")
    details_toggle.wait_for(state="visible")
    check(details_toggle.get_attribute("aria-expanded") == "false", "Mobile insight details are not collapsed by default")
    check(page.locator(".insight-groups").count() == 0, "Collapsed mobile insight still renders distribution groups")
    check(
        page.evaluate("document.documentElement.scrollWidth <= window.innerWidth + 1"),
        "The mobile event page has document-level horizontal overflow",
    )
    page.screenshot(path=str(artifact_dir / "mobile-collapsed.png"), full_page=True)
    details_toggle.click()
    page.locator(".insight-groups").wait_for(state="visible")
    check(details_toggle.get_attribute("aria-expanded") == "true", "aria-expanded did not follow the open state")
    page.wait_for_timeout(250)
    zero_widths = page.locator(".distribution-row").evaluate_all(
        """
        rows => rows
          .filter(row => row.querySelector('.distribution-copy strong')?.textContent.trim() === '0')
          .map(row => row.querySelector('.distribution-track > span')?.style.width || '')
        """
    )
    check(zero_widths, "No zero-value distribution row was rendered")
    check(all(width == "0%" for width in zero_widths), f"Zero-value bar widths are incorrect: {zero_widths}")
    page.screenshot(path=str(artifact_dir / "mobile-expanded.png"), full_page=True)
    page.unroute(event_insight_pattern, route_handler)

    progress("checking mobile task board")
    navigate(page, f"{base_url}/tasks?view=board")
    page.locator(".flow-card").first.wait_for(state="visible")
    task_people = page.locator(".flow-card dl div").evaluate_all(
        """
        rows => rows
          .filter(row => row.querySelector('dt')?.textContent.trim() === '执行人')
          .map(row => row.querySelector('dd')?.textContent.trim() || '')
          .filter(value => value && value !== '—')
        """
    )
    check(task_people, "Task board does not show any readable assignee name")
    check(
        page.evaluate("document.documentElement.scrollWidth <= window.innerWidth + 1"),
        "The mobile task board has document-level horizontal overflow",
    )
    page.screenshot(path=str(artifact_dir / "task-board.png"), full_page=True)

    check(not console_errors, f"Browser console errors: {' | '.join(console_errors)}")
    check(not page_errors, f"Page runtime errors: {' | '.join(page_errors)}")
    check(not failed_requests, f"Failed browser requests: {' | '.join(failed_requests)}")
    check(not bad_api_responses, f"Unexpected API responses: {' | '.join(bad_api_responses)}")

    return {
        "ok": True,
        "apiCounts": api_counts,
        "spatialMap": {
            "located": located_before,
            "unlocated": unlocated_before,
            "filteredGridCode": grid_code,
            "filteredRecords": 1,
            "pagination": False,
            "keyboardMarker": True,
            "mobileStacked": True,
            "liveTiles": live_map_tiles,
        },
        "mobileOverview": {"collapsedByDefault": True, "zeroWidths": zero_widths},
        "navigationUi": {
            "rootRedirect": "/dashboard",
            "serverItems": len(admin_navigation),
            "renderedItems": len(sidebar_labels),
        },
        "dashboardUi": {
            "gridEventStats": len(dashboard["gridEventStats"]),
            "categoryStats": len(dashboard["categoryStats"]),
            "recentEvents": len(dashboard["recentEvents"]),
        },
        "attachmentUi": {
            "eventNo": event_number,
            "filename": attachment_snapshot["attachment"]["originalName"],
            "downloaded": True,
        },
        "taskAttachmentUi": {
            "taskNo": task_number,
            "filename": task_attachment_snapshot["attachment"]["originalName"],
            "downloaded": True,
        },
        "eventFlowUi": {"eventNo": flow_snapshot["event"]["eventNo"], "actions": flow_snapshot["actions"]},
        "accessUi": {
            "fixedRoles": fixed_role_count,
            "communityAssignments": len(access_snapshot.get("assignments", [])),
            "menuPermissionVisible": True,
        },
        "residentSensitiveUi": {
            "temporaryRevealSeconds": 60,
            "clearedOnClose": True,
            "maskedExactSearch": True,
            "auditPurposeVisible": True,
        },
        "readableTaskPeople": sorted(set(task_people)),
        "artifacts": str(artifact_dir),
        "runtime": {
            "consoleErrors": len(console_errors),
            "pageErrors": len(page_errors),
            "failedRequests": len(failed_requests),
            "badApiResponses": len(bad_api_responses),
        },
    }


def run_resident_suite(page: Page, base_url: str, username: str, password: str, artifact_dir: Path) -> dict[str, Any]:
    console_errors: list[str] = []
    page_errors: list[str] = []
    failed_requests: list[str] = []
    bad_api_responses: list[str] = []

    page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
    page.on("pageerror", lambda error: page_errors.append(str(error)))
    def capture_failed_request(request: Any) -> None:
        if is_expected_navigation_abort(request, base_url):
            return
        failed_requests.append(f"{request.method} {request.url} {request.failure or ''}".strip())

    page.on("requestfailed", capture_failed_request)
    page.on("response", lambda response: bad_api_responses.append(f"{response.status} {response.url}")
            if "/api/" in response.url and response.status >= 400
            and not (response.status == 401 and "/api/auth/me" in response.url) else None)

    progress("checking resident root redirect, navigation and attachments")
    with page.expect_response(lambda response: "/api/auth/csrf" in response.url and response.ok):
        navigate(page, f"{base_url}/login")
    page.locator('input[autocomplete="username"]').fill(username)
    page.locator('input[autocomplete="current-password"]').fill(password)
    with page.expect_response(
        lambda response: "/api/auth/login" in response.url and response.request.method == "POST"
    ) as login_info:
        page.get_by_role("button", name="进入工作台").click()
    check(login_info.value.ok, f"Resident login failed with HTTP {login_info.value.status}")
    page.wait_for_url(f"{base_url}/resident/home")
    navigate(page, f"{base_url}/")
    page.wait_for_url(f"{base_url}/resident/home")

    resident_navigation = validate_navigation_snapshot(
        fetch_envelope(page, "/api/auth/navigation"), ["RESIDENT_PORTAL"]
    )
    sidebar_labels = [text.strip() for text in page.locator(".app-menu .el-menu-item").all_inner_texts()]
    check(sidebar_labels == [resident_navigation[0]["name"]], "Resident sidebar is not limited to its server navigation")
    overview_snapshot = fetch_envelope(page, "/api/resident-portal/overview")
    check(overview_snapshot["status"] == 200 and overview_snapshot["body"].get("code") == "OK",
          "Resident overview API failed")
    overview = overview_snapshot["body"].get("data") or {}
    resident_attachment_snapshot = page.evaluate(
        """
        async () => {
          const overviewResponse = await fetch('/api/resident-portal/overview', { credentials: 'same-origin' });
          const overviewEnvelope = await overviewResponse.json();
          for (const event of overviewEnvelope.data?.events || []) {
            const response = await fetch(`/api/resident-portal/events/${event.id}/attachments`, { credentials: 'same-origin' });
            if (!response.ok) continue;
            const envelope = await response.json();
            if (envelope.code === 'OK' && Array.isArray(envelope.data) && envelope.data.length) {
              return { event, attachment: envelope.data[0] };
            }
          }
          return null;
        }
        """
    )
    check(resident_attachment_snapshot is not None, "No resident-owned attachment was available for UI verification")
    resident_event = resident_attachment_snapshot["event"]
    resident_ledger_item = page.locator(".event-ledger li").filter(has_text=resident_event["eventNo"]).first
    resident_ledger_item.wait_for(state="visible")
    resident_ledger_item.get_by_role("button", name="附件", exact=True).click()
    resident_dialog = page.locator(".el-dialog:visible").filter(has_text="附件")
    resident_dialog.locator(".attachment-item").first.wait_for(state="visible")
    check(
        resident_attachment_snapshot["attachment"]["originalName"] in resident_dialog.locator(".attachment-item").first.inner_text(),
        "Resident attachment dialog does not show the uploaded filename",
    )
    with page.expect_download() as resident_download_info:
        resident_dialog.locator(".attachment-item").first.get_by_role("button", name="下载", exact=True).click()
    check(present(resident_download_info.value.suggested_filename), "Resident attachment download has no filename")
    page.screenshot(path=str(artifact_dir / "resident-attachments.png"), full_page=True)
    resident_dialog.get_by_role("button", name="关闭", exact=True).click()

    check(not console_errors, f"Resident browser console errors: {' | '.join(console_errors)}")
    check(not page_errors, f"Resident page runtime errors: {' | '.join(page_errors)}")
    check(not failed_requests, f"Resident failed browser requests: {' | '.join(failed_requests)}")
    check(not bad_api_responses, f"Resident unexpected API responses: {' | '.join(bad_api_responses)}")
    return {
        "rootRedirect": "/resident/home",
        "navigation": [item["code"] for item in resident_navigation],
        "eventNo": resident_event["eventNo"],
        "attachment": resident_attachment_snapshot["attachment"]["originalName"],
        "overviewEvents": len(overview.get("events") or []),
    }


def main() -> int:
    os.umask(0o077)
    isolated_confirmation = required_env("E2E_CONFIRM_ISOLATED")
    if isolated_confirmation != "YES":
        raise RuntimeError("E2E_CONFIRM_ISOLATED must be exactly YES before any request")
    base_url = normalize_base_url(os.environ.get("E2E_BASE_URL", DEFAULT_BASE_URL))
    assert_isolated_target(base_url)
    username = required_env("E2E_USERNAME").strip()
    password = required_env("E2E_PASSWORD")
    resident_username = required_env("E2E_RESIDENT_USERNAME").strip()
    resident_password = required_env("E2E_RESIDENT_PASSWORD")
    validate_password("E2E_PASSWORD", password)
    validate_password("E2E_RESIDENT_PASSWORD", resident_password)
    artifact_dir = prepare_artifact_dir(required_env("E2E_ARTIFACT_DIR"))
    executable = browser_executable()

    page: Page | None = None
    browser = None
    playwright_runtime = None
    try:
        progress("starting Playwright runtime")
        playwright_runtime = sync_playwright().start()
        progress("launching headless Chromium")
        launch_options: dict[str, Any] = {"headless": True}
        if executable:
            launch_options["executable_path"] = executable
        try:
            browser = playwright_runtime.chromium.launch(**launch_options)
        except PlaywrightError as error:
            if executable is None:
                raise RuntimeError(
                    "No browser executable is available; install Chromium with "
                    "`uv run --with playwright==1.61.0 playwright install chromium` "
                    "or set E2E_BROWSER_EXECUTABLE"
                ) from error
            raise
        context = browser.new_context(viewport={"width": 1440, "height": 1000}, locale="zh-CN")
        page = context.new_page()
        install_network_guard(page, base_url)
        page.set_default_timeout(DEFAULT_TIMEOUT_MS)
        page.set_default_navigation_timeout(DEFAULT_TIMEOUT_MS)
        admin_result = run_suite(page, base_url, username, password, artifact_dir)
        context.close()
        resident_context = browser.new_context(viewport={"width": 1440, "height": 1000}, locale="zh-CN")
        page = resident_context.new_page()
        install_network_guard(page, base_url)
        page.set_default_timeout(DEFAULT_TIMEOUT_MS)
        page.set_default_navigation_timeout(DEFAULT_TIMEOUT_MS)
        resident_result = run_resident_suite(
            page, base_url, resident_username, resident_password, artifact_dir
        )
        resident_context.close()
        result = {"ok": True, "administrator": admin_result, "resident": resident_result}
        browser.close()
        browser = None
        playwright_runtime.stop()
        playwright_runtime = None
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except Exception as error:  # noqa: BLE001 - CLI boundary must report one normalized failure.
        print(
            json.dumps(
                {"ok": False, "error": str(error), "artifacts": str(artifact_dir)},
                ensure_ascii=False,
                indent=2,
            ),
            file=sys.stderr,
        )
        return 1
    finally:
        if browser is not None:
            browser.close()
        if playwright_runtime is not None:
            playwright_runtime.stop()


if __name__ == "__main__":
    raise SystemExit(main())
