#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.13,<3.14"
# dependencies = [
#   "greenlet==3.2.4",
#   "playwright==1.61.0",
# ]
# ///

"""Four-role browser navigation and direct-route regression."""

from __future__ import annotations

import os
from pathlib import Path
from urllib.parse import urlsplit

from playwright.sync_api import sync_playwright


def required(name: str, *, secret: bool = False) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value if secret else value.strip()


def check(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


if required("ROLE_UI_CONFIRM_ISOLATED") != "YES":
    raise RuntimeError("ROLE_UI_CONFIRM_ISOLATED must be exactly YES")

base_url = required("ROLE_UI_BASE_URL").rstrip("/")
parsed = urlsplit(base_url)
if parsed.scheme not in {"http", "https"} or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}:
    raise RuntimeError("Role UI regression only accepts an absolute loopback URL")

profiles = [
    {
        "label": "ADMIN",
        "username": required("ROLE_UI_ADMIN_USERNAME"),
        "password": required("ROLE_UI_ADMIN_PASSWORD", secret=True),
        "landing": "/dashboard",
        "menus": ["治理概览", "用户管理", "角色管理", "菜单权限", "事件类别", "网格管理", "居民档案", "治理事件", "网格任务"],
        "forbidden": ["/resident/home"],
    },
    {
        "label": "COMMUNITY",
        "username": required("ROLE_UI_COMMUNITY_USERNAME"),
        "password": required("ROLE_UI_COMMUNITY_PASSWORD", secret=True),
        "landing": "/dashboard",
        "menus": ["治理概览", "网格管理", "居民档案", "治理事件", "网格任务"],
        "forbidden": ["/system/users", "/system/roles", "/system/menus"],
    },
    {
        "label": "GRID",
        "username": required("ROLE_UI_GRID_USERNAME"),
        "password": required("ROLE_UI_GRID_PASSWORD", secret=True),
        "landing": "/dashboard",
        "menus": ["治理概览", "治理事件", "网格任务"],
        "forbidden": ["/grids", "/residents", "/system/users"],
    },
    {
        "label": "RESIDENT",
        "username": required("ROLE_UI_RESIDENT_USERNAME"),
        "password": required("ROLE_UI_RESIDENT_PASSWORD", secret=True),
        "landing": "/resident/home",
        "menus": ["居民服务台"],
        "forbidden": ["/dashboard", "/events", "/tasks"],
    },
]

chrome_path = os.environ.get(
    "ROLE_UI_BROWSER_EXECUTABLE",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
)
if not Path(chrome_path).is_file():
    chrome_path = None

console_errors: list[str] = []
page_errors: list[str] = []
failed_requests: list[str] = []
results: list[dict[str, object]] = []

with sync_playwright() as runtime:
    launch_options = {"headless": True}
    if chrome_path:
        launch_options["executable_path"] = chrome_path
    browser = runtime.chromium.launch(**launch_options)
    context = browser.new_context(viewport={"width": 1440, "height": 1000}, locale="zh-CN")
    page = context.new_page()
    page.set_default_timeout(15_000)
    page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
    page.on("pageerror", lambda error: page_errors.append(str(error)))
    page.on("requestfailed", lambda request: failed_requests.append(f"{request.method} {request.url}"))

    for profile in profiles:
        page.goto(f"{base_url}/login", wait_until="domcontentloaded")
        page.get_by_placeholder("请输入用户名").fill(str(profile["username"]))
        page.get_by_placeholder("请输入密码").fill(str(profile["password"]))
        page.get_by_role("button", name="进入工作台").click()
        page.wait_for_url(f"**{profile['landing']}")
        page.locator(".app-menu .el-menu-item").first.wait_for(state="visible")
        menus = [text.strip() for text in page.locator(".app-menu .el-menu-item").all_inner_texts()]
        check(menus == profile["menus"], f"{profile['label']} menus: {menus}")

        for forbidden_path in profile["forbidden"]:
            page.goto(f"{base_url}{forbidden_path}", wait_until="domcontentloaded")
            page.wait_for_url("**/forbidden")
            check(page.get_by_text("无权访问", exact=True).is_visible(), f"{profile['label']} direct route {forbidden_path}")

        page.locator(".user-menu").click()
        page.get_by_text("退出登录", exact=True).click()
        page.wait_for_url("**/login")
        check(page.locator(".app-menu .el-menu-item").count() == 0, f"{profile['label']} navigation leaked after logout")
        results.append({"role": profile["label"], "menus": menus, "landing": profile["landing"]})

    context.close()
    browser.close()

check(not console_errors, f"Console errors: {' | '.join(console_errors)}")
check(not page_errors, f"Page errors: {' | '.join(page_errors)}")
check(not failed_requests, f"Failed requests: {' | '.join(failed_requests)}")

print({
    "result": "ROLE NAVIGATION UI PASS",
    "roles": results,
    "consoleErrors": len(console_errors),
    "pageErrors": len(page_errors),
    "failedRequests": len(failed_requests),
})
