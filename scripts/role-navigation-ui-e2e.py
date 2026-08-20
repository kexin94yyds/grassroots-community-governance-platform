#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.13,<3.14"
# dependencies = [
#   "greenlet==3.2.4",
#   "playwright==1.61.0",
# ]
# ///

"""Four-role workbench navigation, write-affordance and session regression."""

from __future__ import annotations

import json
import os
from pathlib import Path
from urllib.parse import urlsplit

from playwright.sync_api import Page, Response, sync_playwright


ROOT = Path(__file__).resolve().parent.parent
MATRIX_PATH = ROOT / "scripts" / "role-workbench-matrix.json"
MATRIX = json.loads(MATRIX_PATH.read_text(encoding="utf-8"))
ROLE_ENV_NAMES = {
    "SYSTEM_ADMIN": "ADMIN",
    "COMMUNITY_STAFF": "COMMUNITY",
    "GRID_WORKER": "GRID",
    "RESIDENT": "RESIDENT",
}


def required(name: str, *, secret: bool = False) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value if secret else value.strip()


def check(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def origin(value: str) -> tuple[str, str, int]:
    parsed = urlsplit(value)
    scheme = parsed.scheme.lower()
    return scheme, (parsed.hostname or "").lower(), parsed.port or (443 if scheme == "https" else 80)


def assert_loopback(value: str) -> None:
    parsed = urlsplit(value)
    check(parsed.scheme in {"http", "https"}, "ROLE_UI_BASE_URL must use HTTP(S)")
    check(parsed.hostname in {"127.0.0.1", "localhost", "::1"}, "Role UI regression only accepts an absolute loopback URL")


def capture_failed_request(request: object, base_url: str, failures: list[str]) -> None:
    failure = str(getattr(request, "failure", "") or "")
    request_url = str(getattr(request, "url", ""))
    method = str(getattr(request, "method", ""))
    if method == "GET" and "ERR_ABORTED" in failure and origin(request_url) == origin(base_url):
        return
    request_host = (urlsplit(request_url).hostname or "").lower()
    if method == "GET" and request_host == "tile.openstreetmap.org" and "ERR_ABORTED" in failure:
        # Rapid role-navigation checks leave the map immediately after it renders;
        # Chrome cancels remaining external tiles. Non-abort tile failures still fail.
        return
    failures.append(f"{method} {request_url} {failure}".strip())


def click_menu_entry(page: Page, route_path: str, expected_url: str, expected_routes: list[str]) -> None:
    items = page.locator(".app-menu .el-menu-item")
    check(items.count() == len(expected_routes),
          f"Sidebar entry count mismatch: actual={items.count()} expected={len(expected_routes)}")
    check(route_path in expected_routes, f"Missing sidebar contract entry for {route_path}")
    item = items.nth(expected_routes.index(route_path))
    item.click()
    page.wait_for_url(f"**{expected_url}")
    page.wait_for_load_state("networkidle")


def visible_write_check(page: Page, check_contract: dict[str, str]) -> None:
    button = page.get_by_role("button", name=check_contract["buttonText"], exact=False).first
    button.wait_for(state="visible")
    check(button.is_enabled(), f"Write action is disabled: {check_contract['id']}")
    if check_contract.get("direct"):
        return
    button.click()
    submit = page.get_by_role("button", name=check_contract["submitText"], exact=False).last
    submit.wait_for(state="visible")
    check(submit.is_enabled(), f"Write form is not executable: {check_contract['id']}")
    cancel = page.get_by_role("button", name="取消", exact=True).last
    if cancel.count() == 1 and cancel.is_visible():
        cancel.click()


def main() -> int:
    if required("ROLE_UI_CONFIRM_ISOLATED") != "YES":
        raise RuntimeError("ROLE_UI_CONFIRM_ISOLATED must be exactly YES")
    base_url = required("ROLE_UI_BASE_URL").rstrip("/")
    assert_loopback(base_url)
    profiles = []
    for role, contract in MATRIX["roles"].items():
        env_name = ROLE_ENV_NAMES[role]
        profiles.append({
            "role": role,
            "contract": contract,
            "username": required(f"ROLE_UI_{env_name}_USERNAME"),
            "password": required(f"ROLE_UI_{env_name}_PASSWORD", secret=True),
        })

    console_errors: list[str] = []
    page_errors: list[str] = []
    failed_requests: list[str] = []
    bad_api_responses: list[str] = []
    results: list[dict[str, object]] = []

    with sync_playwright() as runtime:
        launch_options = {"headless": True}
        executable = os.environ.get("ROLE_UI_BROWSER_EXECUTABLE", "").strip()
        if not executable:
            local_chrome = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
            if local_chrome.is_file():
                executable = str(local_chrome)
        if executable:
            check(Path(executable).is_file(), f"ROLE_UI_BROWSER_EXECUTABLE does not exist: {executable}")
            launch_options["executable_path"] = executable
        browser = runtime.chromium.launch(**launch_options)
        context = browser.new_context(viewport={"width": 1440, "height": 1000}, locale="zh-CN")
        page: Page = context.new_page()
        page.set_default_timeout(15_000)
        page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
        page.on("pageerror", lambda error: page_errors.append(str(error)))
        page.on("requestfailed", lambda request: capture_failed_request(request, base_url, failed_requests))

        def capture_response(response: Response) -> None:
            if "/api/" not in response.url or response.status < 400:
                return
            if response.status == 401 and response.url.endswith("/api/auth/me"):
                return
            bad_api_responses.append(f"{response.status} {response.url}")

        page.on("response", capture_response)

        for profile in profiles:
            role = profile["role"]
            contract = profile["contract"]
            before_errors = tuple(len(values) for values in (console_errors, page_errors, failed_requests, bad_api_responses))
            page.goto(f"{base_url}/login", wait_until="domcontentloaded")
            page.get_by_placeholder("请输入用户名").fill(str(profile["username"]))
            page.get_by_placeholder("请输入密码").fill(str(profile["password"]))
            page.get_by_role("button", name="进入工作台").click()
            page.wait_for_url(f"**{contract['homePath']}")
            page.wait_for_load_state("networkidle")

            menu_items = page.locator(".app-menu .el-menu-item")
            expected_routes = [entry["routePath"] for entry in contract["navigation"]]
            check(menu_items.count() == len(expected_routes),
                  f"{role} sidebar count mismatch: actual={menu_items.count()} expected={len(expected_routes)}")
            stats = page.locator("[data-role-stats], .role-workbench-stats, .workbench-stats, .workbench-metrics")
            check(stats.count() >= 1 and stats.first.is_visible(), f"{role} statistics area is missing")

            clicked = []
            for entry in contract["navigation"]:
                click_menu_entry(page, entry["routePath"], entry["routePath"], expected_routes)
                clicked.append(entry["code"])

            write_affordances = []
            for write_check in contract.get("uiWriteChecks", [])[:2]:
                click_menu_entry(page, write_check["routePath"], write_check["routePath"], expected_routes)
                visible_write_check(page, write_check)
                write_affordances.append(write_check["id"])

            for hidden_path in contract.get("hiddenRoutes", []):
                page.goto(f"{base_url}{hidden_path}", wait_until="domcontentloaded")
                page.wait_for_url("**/forbidden")
                check(page.get_by_text("无权访问", exact=True).is_visible(),
                      f"{role} opening-report-hidden route leaked: {hidden_path}")

            own_routes = set(expected_routes)
            other_routes = {
                entry["routePath"]
                for other_role, other_contract in MATRIX["roles"].items()
                if other_role != role
                for entry in other_contract["navigation"]
                if entry["routePath"] not in own_routes and entry["routePath"] != "/announcements"
            }
            for forbidden_path in sorted(other_routes):
                page.goto(f"{base_url}{forbidden_path}", wait_until="domcontentloaded")
                page.wait_for_url("**/forbidden")
                check(page.get_by_text("无权访问", exact=True).is_visible(), f"{role} direct route {forbidden_path}")

            page.locator(".user-menu").click()
            page.get_by_text("退出登录", exact=True).click()
            page.wait_for_url("**/login")
            check(page.locator(".app-menu .el-menu-item").count() == 0, f"{role} navigation leaked after logout")
            after_errors = tuple(len(values) for values in (console_errors, page_errors, failed_requests, bad_api_responses))
            if after_errors != before_errors:
                console_delta = console_errors[before_errors[0]:after_errors[0]]
                page_delta = page_errors[before_errors[1]:after_errors[1]]
                request_delta = failed_requests[before_errors[2]:after_errors[2]]
                api_delta = bad_api_responses[before_errors[3]:after_errors[3]]
                raise AssertionError(
                    f"{role} browser errors: console={console_delta}; page={page_delta}; "
                    f"requests={request_delta}; api={api_delta}"
                )
            results.append({"role": role, "entries": len(clicked), "writeAffordances": write_affordances, "home": contract["homePath"]})

        context.close()
        browser.close()

    check(not console_errors, f"Console errors: {' | '.join(console_errors)}")
    check(not page_errors, f"Page errors: {' | '.join(page_errors)}")
    check(not failed_requests, f"Failed requests: {' | '.join(failed_requests)}")
    check(not bad_api_responses, f"Bad API responses: {' | '.join(bad_api_responses)}")
    print({
        "result": "ROLE NAVIGATION UI PASS",
        "contractVersion": MATRIX["contractVersion"],
        "roles": results,
        "writeAffordances": sum((item["writeAffordances"] for item in results), []),
        "consoleErrors": len(console_errors),
        "pageErrors": len(page_errors),
        "failedRequests": len(failed_requests),
        "badApiResponses": len(bad_api_responses),
    })
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # noqa: BLE001 - CLI boundary normalizes one failure.
        print(f"ROLE NAVIGATION UI FAIL: {error}")
        raise SystemExit(1)
