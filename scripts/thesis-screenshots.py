#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.13,<3.14"
# dependencies = [
#   "greenlet==3.2.4",
#   "playwright==1.61.0",
# ]
# ///

"""Capture a reproducible thesis figure set from an isolated demo database."""

from __future__ import annotations

import json
import os
import sys
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit, urlunsplit

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import Page, sync_playwright

DEFAULT_BASE_URL = "http://localhost:5173"
DEFAULT_CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
DEFAULT_TIMEOUT_MS = 15_000
DESKTOP_VIEWPORT = {"width": 1920, "height": 1080}
MOBILE_VIEWPORT = {"width": 390, "height": 844}

FIGURES = [
    ("01-login.png", "系统登录界面", "展示平台身份认证入口与系统定位。"),
    ("02-dashboard.png", "社区治理概览", "展示网格、居民、重点人群及事件处置阶段统计。"),
    ("03-user-permissions.png", "用户与角色管理", "展示系统管理员、社区工作人员和网格员账号。"),
    ("04-grid-responsibility-map.png", "网格空间地图", "真实坐标点位与待定位网格同屏呈现。"),
    ("05-resident-cards.png", "居民档案卡片", "展示居民、家庭户、重点人群标签与所属网格。"),
    ("06-event-flow.png", "治理事件流程追踪", "展示待受理、处理中、待复核和已办结等阶段。"),
    ("07-task-board.png", "网格任务执行看板", "展示任务在接单、处置、复核和完成阶段的分布。"),
    ("08-mobile-events.png", "移动端治理事件", "展示窄屏下概览折叠、筛选与事件卡片布局。"),
]


def required_env(name: str) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def normalize_base_url(raw_value: str) -> str:
    parsed = urlsplit(raw_value.strip())
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise RuntimeError("THESIS_BASE_URL must be an absolute HTTP(S) URL")
    if parsed.query or parsed.fragment:
        raise RuntimeError("THESIS_BASE_URL must not contain a query string or fragment")
    return urlunsplit((parsed.scheme, parsed.netloc, parsed.path.rstrip("/"), "", ""))


def browser_executable() -> str | None:
    configured = os.environ.get("THESIS_BROWSER_EXECUTABLE", "").strip()
    if configured:
        executable = Path(configured).expanduser()
        if not executable.is_file():
            raise RuntimeError("THESIS_BROWSER_EXECUTABLE does not point to a file")
        return str(executable)
    default_chrome = Path(DEFAULT_CHROME_PATH)
    return str(default_chrome) if default_chrome.is_file() else None


def progress(message: str) -> None:
    print(f"[thesis-screenshots] {message}", file=sys.stderr, flush=True)


def navigate(page: Page, url: str) -> None:
    page.goto(url, wait_until="domcontentloaded")
    page.wait_for_load_state("networkidle")
    page.evaluate("document.fonts ? document.fonts.ready : Promise.resolve()")
    page.evaluate("window.scrollTo(0, 0)")
    page.wait_for_timeout(250)


def screenshot(page: Page, output_dir: Path, filename: str) -> None:
    page.screenshot(path=str(output_dir / filename), full_page=False)


def validate_demo_counts(page: Page) -> dict[str, int]:
    snapshot = page.evaluate(
        """
        async () => {
          const paths = {
            grids: '/api/grids?page=1&size=20&areaType=GRID',
            residents: '/api/residents?page=1&size=20',
            events: '/api/events?page=1&size=20',
            tasks: '/api/tasks?page=1&size=20'
          };
          const pairs = await Promise.all(Object.entries(paths).map(async ([key, path]) => {
            const response = await fetch(path, { credentials: 'same-origin' });
            const body = await response.json();
            return [key, { status: response.status, body }];
          }));
          return Object.fromEntries(pairs);
        }
        """
    )
    minimums = {"grids": 4, "residents": 4, "events": 16, "tasks": 15}
    counts: dict[str, int] = {}
    for key, minimum in minimums.items():
        result = snapshot[key]
        if result["status"] != 200 or result["body"].get("code") != "OK":
            raise AssertionError(f"{key} demo API is unavailable")
        total = int(result["body"].get("data", {}).get("total", 0))
        if total < minimum:
            raise AssertionError(f"{key} demo data requires at least {minimum} records, got {total}")
        counts[key] = total
    return counts


def run_suite(page: Page, base_url: str, username: str, password: str, output_dir: Path) -> dict[str, Any]:
    console_errors: list[str] = []
    page_errors: list[str] = []
    failed_requests: list[str] = []
    bad_api_responses: list[str] = []

    page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
    page.on("pageerror", lambda error: page_errors.append(str(error)))
    page.on(
        "requestfailed",
        lambda request: failed_requests.append(f"{request.method} {request.url} {request.failure or ''}".strip()),
    )

    def capture_bad_api(response: Any) -> None:
        if (
            "/api/" in response.url
            and response.status >= 400
            and not (response.status == 401 and "/api/auth/me" in response.url)
        ):
            bad_api_responses.append(f"{response.status} {response.url}")

    page.on("response", capture_bad_api)

    progress("capturing login")
    with page.expect_response(lambda response: "/api/auth/csrf" in response.url and response.ok):
        navigate(page, f"{base_url}/login")
    page.locator('input[autocomplete="username"]').wait_for(state="visible")
    screenshot(page, output_dir, "01-login.png")

    page.locator('input[autocomplete="username"]').fill(username)
    page.locator('input[autocomplete="current-password"]').fill(password)
    with page.expect_response(
        lambda response: "/api/auth/login" in response.url and response.request.method == "POST"
    ) as login_info:
        page.get_by_role("button", name="进入工作台").click()
    if not login_info.value.ok:
        raise AssertionError(f"Login failed with HTTP {login_info.value.status}")
    page.wait_for_url(f"{base_url}/dashboard")
    page.wait_for_load_state("networkidle")

    counts = validate_demo_counts(page)
    pages = [
        ("dashboard", ".governance-track", "02-dashboard.png"),
        ("system/users?view=card", ".record-card", "03-user-permissions.png"),
        ("grids?view=map", ".spatial-map-stage", "04-grid-responsibility-map.png"),
        ("residents?view=card", ".record-card", "05-resident-cards.png"),
        ("events?view=trace", ".flow-card", "06-event-flow.png"),
        ("tasks?view=board", ".flow-card", "07-task-board.png"),
    ]
    for path, ready_selector, filename in pages:
        progress(f"capturing {filename}")
        page.set_viewport_size(DESKTOP_VIEWPORT)
        navigate(page, f"{base_url}/{path}")
        page.locator(ready_selector).first.wait_for(state="visible")
        screenshot(page, output_dir, filename)

    progress("capturing mobile events")
    page.set_viewport_size(MOBILE_VIEWPORT)
    navigate(page, f"{base_url}/events?view=card")
    page.locator(".record-card").first.wait_for(state="visible")
    toggle = page.locator(".insight-details-toggle")
    toggle.wait_for(state="visible")
    if toggle.get_attribute("aria-expanded") != "false":
        raise AssertionError("Mobile insight details must be collapsed for the thesis screenshot")
    if not page.evaluate("document.documentElement.scrollWidth <= window.innerWidth + 1"):
        raise AssertionError("Mobile thesis screenshot has document-level horizontal overflow")
    screenshot(page, output_dir, "08-mobile-events.png")

    if console_errors:
        raise AssertionError(f"Browser console errors: {' | '.join(console_errors)}")
    if page_errors:
        raise AssertionError(f"Page runtime errors: {' | '.join(page_errors)}")
    if failed_requests:
        raise AssertionError(f"Failed browser requests: {' | '.join(failed_requests)}")
    if bad_api_responses:
        raise AssertionError(f"Unexpected API responses: {' | '.join(bad_api_responses)}")

    manifest = {
        "generatedAt": datetime.now(UTC).isoformat(),
        "viewport": {"desktop": DESKTOP_VIEWPORT, "mobile": MOBILE_VIEWPORT},
        "demoCounts": counts,
        "runtime": {
            "consoleErrors": 0,
            "pageErrors": 0,
            "failedRequests": 0,
            "badApiResponses": 0,
        },
        "figures": [
            {"file": filename, "title": title, "caption": caption}
            for filename, title, caption in FIGURES
        ],
    }
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return {"ok": True, "output": str(output_dir), **manifest}


def main() -> int:
    base_url = normalize_base_url(os.environ.get("THESIS_BASE_URL", DEFAULT_BASE_URL))
    username = required_env("THESIS_USERNAME").strip()
    password = required_env("THESIS_PASSWORD")
    project_root = Path(__file__).resolve().parent.parent
    output_dir = Path(
        os.environ.get("THESIS_SCREENSHOT_DIR", str(project_root / "docs" / "thesis" / "screenshots"))
    ).expanduser().resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    for filename, _, _ in FIGURES:
        (output_dir / filename).unlink(missing_ok=True)
    (output_dir / "manifest.json").unlink(missing_ok=True)
    (output_dir / "failure.png").unlink(missing_ok=True)

    executable = browser_executable()
    page: Page | None = None
    browser = None
    playwright_runtime = None
    try:
        playwright_runtime = sync_playwright().start()
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
                    "or set THESIS_BROWSER_EXECUTABLE"
                ) from error
            raise
        context = browser.new_context(viewport=DESKTOP_VIEWPORT, locale="zh-CN", color_scheme="light")
        page = context.new_page()
        page.set_default_timeout(DEFAULT_TIMEOUT_MS)
        page.set_default_navigation_timeout(DEFAULT_TIMEOUT_MS)
        result = run_suite(page, base_url, username, password, output_dir)
        context.close()
        browser.close()
        browser = None
        playwright_runtime.stop()
        playwright_runtime = None
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except Exception as error:  # noqa: BLE001 - CLI boundary normalizes one failure result.
        if page is not None:
            try:
                page.screenshot(path=str(output_dir / "failure.png"), full_page=False)
            except Exception:
                pass
        print(
            json.dumps({"ok": False, "error": str(error), "output": str(output_dir)}, ensure_ascii=False, indent=2),
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
