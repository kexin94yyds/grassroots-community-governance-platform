#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.13,<3.14"
# dependencies = [
#   "greenlet==3.2.4",
#   "playwright==1.61.0",
# ]
# ///

"""Stateful isolated registration approval/rejection browser contract."""

from __future__ import annotations

import json
import os
import secrets
import sys
from pathlib import Path
from typing import Any
from urllib.parse import quote, urlsplit, urlunsplit

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import Page, sync_playwright

DEFAULT_BASE_URL = "http://localhost:5173"
DEFAULT_CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
DEFAULT_TIMEOUT_MS = 15_000


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
    return urlunsplit((parsed.scheme, parsed.netloc, parsed.path.rstrip("/"), "", ""))


def origin(value: str) -> tuple[str, str, int]:
    parsed = urlsplit(value)
    scheme = {"ws": "http", "wss": "https"}.get(parsed.scheme.lower(), parsed.scheme.lower())
    return scheme, (parsed.hostname or "").lower(), parsed.port or (443 if scheme == "https" else 80)


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
            "Remote E2E_BASE_URL requires an exact E2E_ALLOWED_REMOTE_HOST match plus "
            "E2E_ALLOW_REMOTE_TARGET=YES and E2E_CONFIRM_REMOTE_DISPOSABLE=YES"
        )


def validate_password(name: str, value: str) -> None:
    if not 12 <= len(value) <= 128 or not (
        any(character.islower() for character in value)
        and any(character.isupper() for character in value)
        and any(character.isdigit() for character in value)
        and any(not character.isalnum() for character in value)
    ):
        raise RuntimeError(
            f"{name} must contain 12-128 characters with upper-case, lower-case, numeric and special characters"
        )


def check(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def progress(message: str) -> None:
    print(f"[registration-ui-e2e] {message}", file=sys.stderr, flush=True)


def browser_executable() -> str | None:
    configured = os.environ.get("E2E_BROWSER_EXECUTABLE", "").strip()
    if configured:
        executable = Path(configured).expanduser()
        if not executable.is_file():
            raise RuntimeError("E2E_BROWSER_EXECUTABLE does not point to a file")
        return str(executable)
    default_chrome = Path(DEFAULT_CHROME_PATH)
    return str(default_chrome) if default_chrome.is_file() else None


def install_network_guard(page: Page, base_url: str) -> Any:
    approved_origin = origin(base_url)
    session = page.context.new_cdp_session(page)

    def guard(event: dict[str, Any]) -> None:
        request_id = event["requestId"]
        request_url = event["request"]["url"]
        parsed = urlsplit(request_url)
        try:
            if parsed.scheme in {"about", "blob", "data"} or origin(request_url) == approved_origin:
                session.send("Fetch.continueRequest", {"requestId": request_id})
                return
            session.send("Fetch.failRequest", {"requestId": request_id, "errorReason": "BlockedByClient"})
        except PlaywrightError as error:
            if page.is_closed() or "Target page, context or browser has been closed" in str(error):
                return
            raise

    session.on("Fetch.requestPaused", guard)
    session.send("Fetch.enable", {"patterns": [{"urlPattern": "*", "requestStage": "Request"}]})
    return session


def new_audit() -> dict[str, list[str]]:
    return {"console": [], "page": [], "failed": [], "bad": []}


def attach_audit(page: Page, audit: dict[str, list[str]], *, allow_login_401: bool = False) -> None:
    def console_message(message: Any) -> None:
        if message.type != "error":
            return
        if allow_login_401 and message.text == (
            "Failed to load resource: the server responded with a status of 401 (Unauthorized)"
        ):
            return
        audit["console"].append(message.text)

    page.on("console", console_message)
    page.on("pageerror", lambda error: audit["page"].append(str(error)))

    def request_failed(request: Any) -> None:
        failure = str(request.failure or "")
        if request.method == "GET" and "ERR_ABORTED" in failure:
            return
        audit["failed"].append(f"{request.method} {request.url} {failure}".strip())

    def bad_response(response: Any) -> None:
        if "/api/" not in response.url or response.status < 400:
            return
        if response.status == 401 and (
            "/api/auth/me" in response.url or (allow_login_401 and "/api/auth/login" in response.url)
        ):
            return
        audit["bad"].append(f"{response.status} {response.url}")

    page.on("requestfailed", request_failed)
    page.on("response", bad_response)


def assert_audit_clean(audit: dict[str, list[str]], label: str) -> None:
    for key, values in audit.items():
        check(not values, f"{label} {key} errors: {' | '.join(values)}")


def navigate(page: Page, url: str) -> None:
    page.goto(url, wait_until="domcontentloaded")
    check(origin(page.url) == origin(url), f"Browser navigation escaped the approved origin: {page.url}")
    page.wait_for_load_state("networkidle")


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


def mutate_envelope(page: Page, method: str, path: str, body: dict[str, Any]) -> dict[str, Any]:
    return page.evaluate(
        """
        async ({ method, path, body }) => {
          await fetch('/api/auth/csrf', { credentials: 'same-origin' });
          const cookie = document.cookie.split('; ').find(item => item.startsWith('XSRF-TOKEN='));
          if (!cookie) throw new Error('Missing XSRF-TOKEN cookie');
          const token = decodeURIComponent(cookie.slice('XSRF-TOKEN='.length));
          const response = await fetch(path, {
            method,
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
            body: JSON.stringify(body)
          });
          return { status: response.status, body: await response.json() };
        }
        """,
        {"method": method, "path": path, "body": body},
    )


def login_admin(page: Page, base_url: str, username: str, password: str) -> None:
    navigate(page, f"{base_url}/login")
    page.locator('input[autocomplete="username"]').fill(username)
    page.locator('input[autocomplete="current-password"]').fill(password)
    with page.expect_response(
        lambda response: "/api/auth/login" in response.url and response.request.method == "POST"
    ) as login_info:
        page.get_by_role("button", name="进入工作台").click()
    check(login_info.value.ok, f"Administrator login failed with HTTP {login_info.value.status}")
    page.wait_for_url(f"{base_url}/dashboard")


def register_via_ui(browser: Any, guards: list[Any], base_url: str, account: dict[str, str]) -> None:
    context = browser.new_context(viewport={"width": 1280, "height": 900}, locale="zh-CN")
    page = context.new_page()
    guards.append(install_network_guard(page, base_url))
    audit = new_audit()
    attach_audit(page, audit)
    page.set_default_timeout(DEFAULT_TIMEOUT_MS)
    page.set_default_navigation_timeout(DEFAULT_TIMEOUT_MS)
    try:
        navigate(page, f"{base_url}/register?type={account['accountType']}")
        if account["accountType"] == "RESIDENT":
            page.get_by_role("tab", name="居民注册").click()
        else:
            page.get_by_role("tab", name="工作人员申请").click()
        page.locator('input[autocomplete="username"]').fill(account["username"])
        page.locator('input[autocomplete="name"]').fill(account["realName"])
        page.locator('input[autocomplete="tel"]').fill(account["phone"])
        if account["accountType"] == "RESIDENT":
            page.locator(".el-form-item").filter(has_text="身份证号").locator("input").first.fill(account["idCard"])
        password_inputs = page.locator('input[autocomplete="new-password"]')
        password_inputs.nth(0).fill(account["password"])
        password_inputs.nth(1).fill(account["password"])
        page.locator("textarea").fill("本轮隔离注册审核浏览器验收")
        with page.expect_response(
            lambda response: "/api/auth/register" in response.url and response.request.method == "POST"
        ) as registration_info:
            page.get_by_role("button", name="提交注册申请").click()
        response = registration_info.value
        check(response.ok, f"{account['label']} registration failed with HTTP {response.status}")
        check(response.json().get("code") == "OK", f"{account['label']} registration business response failed")
        page.get_by_text("注册信息已安全提交", exact=True).wait_for(state="visible")
        assert_audit_clean(audit, account["label"])
    finally:
        context.close()


def attempt_login(
    browser: Any,
    guards: list[Any],
    base_url: str,
    account: dict[str, str],
    expected_path: str | None,
) -> None:
    context = browser.new_context(viewport={"width": 1100, "height": 760}, locale="zh-CN")
    page = context.new_page()
    guards.append(install_network_guard(page, base_url))
    audit = new_audit()
    attach_audit(page, audit, allow_login_401=expected_path is None)
    page.set_default_timeout(DEFAULT_TIMEOUT_MS)
    page.set_default_navigation_timeout(DEFAULT_TIMEOUT_MS)
    try:
        navigate(page, f"{base_url}/login")
        page.locator('input[autocomplete="username"]').fill(account["username"])
        page.locator('input[autocomplete="current-password"]').fill(account["password"])
        with page.expect_response(
            lambda response: "/api/auth/login" in response.url and response.request.method == "POST"
        ) as login_info:
            page.get_by_role("button", name="进入工作台").click()
        response = login_info.value
        if expected_path is None:
            check(response.status == 401, f"{account['label']} unexpectedly logged in with HTTP {response.status}")
            page.locator(".login-alert").wait_for(state="visible")
            check(urlsplit(page.url).path == "/login", f"{account['label']} left login after denied authentication")
        else:
            check(response.ok, f"{account['label']} approved login failed with HTTP {response.status}")
            page.wait_for_url(f"{base_url}{expected_path}")
        assert_audit_clean(audit, f"{account['label']} login")
    finally:
        context.close()


def find_user(page: Page, username: str) -> dict[str, Any]:
    result = fetch_envelope(page, f"/api/system/users?keyword={quote(username)}&page=1&size=20")
    check(result["status"] == 200 and result["body"].get("code") == "OK", "User lookup failed")
    matches = [item for item in result["body"]["data"]["items"] if item.get("username") == username]
    check(len(matches) == 1, f"Expected exactly one user for {username}")
    detail = fetch_envelope(page, f"/api/system/users/{matches[0]['id']}")
    check(detail["status"] == 200 and detail["body"].get("code") == "OK", "User detail lookup failed")
    return detail["body"]["data"]


def open_review_dialog(page: Page, base_url: str, username: str) -> Any:
    navigate(page, f"{base_url}/system/users")
    search = page.locator('.query-bar input[placeholder="姓名或用户名"]')
    search.fill(username)
    with page.expect_response(
        lambda response: "/api/system/users?" in response.url and f"keyword={username}" in response.url
    ):
        page.get_by_role("button", name="查询", exact=True).click()
    page.get_by_role("button", name="审核注册", exact=True).first.click()
    dialog = page.locator(".registration-review-dialog")
    dialog.wait_for(state="visible")
    check(page.locator("el-radio, el-radio-group, el-radio-button").count() == 0, "Radio rendered as unknown custom elements")
    check(
        dialog.locator('.registration-review-decision input[type="radio"]').first.is_checked(),
        "Registration review did not default to APPROVE",
    )
    return dialog


def review_account(
    page: Page,
    base_url: str,
    account: dict[str, str],
    decision: str,
    review_requests: list[str],
    *,
    test_cancel: bool = False,
    test_empty: bool = False,
    test_double_submit: bool = False,
) -> dict[str, Any]:
    before = find_user(page, account["username"])
    check(before["approvalStatus"] == "PENDING" and before["status"] == "DISABLED", "Review fixture is not pending")
    dialog = open_review_dialog(page, base_url, account["username"])

    if test_cancel:
        request_count = len(review_requests)
        dialog.locator(".registration-review-cancel").click()
        dialog.wait_for(state="hidden")
        check(len(review_requests) == request_count, "Cancelling review emitted a review request")
        after_cancel = find_user(page, account["username"])
        check(
            after_cancel["approvalStatus"] == "PENDING" and after_cancel["version"] == before["version"],
            "Cancelling review changed registration state",
        )
        dialog = open_review_dialog(page, base_url, account["username"])

    if decision == "REJECT":
        dialog.get_by_text("驳回", exact=True).click()
        check(
            dialog.locator('.registration-review-decision input[type="radio"]').nth(1).is_checked(),
            "Radio did not switch to REJECT",
        )
        if test_empty:
            request_count = len(review_requests)
            dialog.locator(".registration-review-submit").click()
            dialog.get_by_text("驳回注册申请必须填写原因", exact=True).wait_for(state="visible")
            check(len(review_requests) == request_count, "Empty rejection reason reached the backend")
        dialog.locator("textarea").fill("隔离浏览器验收：申请资料需补充后重新提交")
    elif account["accountType"] == "STAFF":
        if test_empty:
            request_count = len(review_requests)
            dialog.locator(".registration-review-submit").click()
            dialog.get_by_text("批准工作人员申请时至少分配一个内部角色", exact=True).wait_for(state="visible")
            check(len(review_requests) == request_count, "Staff approval without a role reached the backend")
        dialog.locator(".el-select").click()
        page.locator(".el-select-dropdown:visible .el-select-dropdown__item").filter(has_text="社区工作人员").click()

    request_count = len(review_requests)
    with page.expect_response(
        lambda response: "/registration-review" in response.url and response.request.method == "POST"
    ) as review_info:
        submit = dialog.locator(".registration-review-submit")
        if test_double_submit:
            submit.evaluate("button => { button.click(); button.click(); }")
        else:
            submit.click()
    response = review_info.value
    check(response.ok and response.json().get("code") == "OK", f"{account['label']} review failed")
    dialog.wait_for(state="hidden")
    check(len(review_requests) == request_count + 1, "Review loading guard allowed duplicate submission")

    reviewed = find_user(page, account["username"])
    expected_status = "APPROVED" if decision == "APPROVE" else "REJECTED"
    check(reviewed["approvalStatus"] == expected_status, f"{account['label']} review status mismatch")
    if decision == "APPROVE" and account["accountType"] == "STAFF":
        check(reviewed["roles"] == ["COMMUNITY_STAFF"], "Approved staff received the wrong role")
    if decision == "APPROVE" and account["accountType"] == "RESIDENT":
        check(reviewed["roles"] == ["RESIDENT"], "Approved resident did not receive only RESIDENT")
    if decision == "REJECT":
        check(not reviewed["roles"] and reviewed["status"] == "DISABLED", "Rejected account gained roles or access")
    return reviewed


def make_account(label: str, account_type: str, run_tag: str, index: int) -> dict[str, str]:
    phone = f"13{6 + index}{secrets.randbelow(100_000_000):08d}"
    return {
        "label": label,
        "accountType": account_type,
        "username": f"reg-{account_type.lower()}-{index}-{run_tag}",
        "password": f"{secrets.token_urlsafe(24)}Aa1!",
        "realName": f"注册验收{label}{run_tag[:4]}",
        "phone": phone,
        "idCard": "",
    }


def main() -> int:
    os.umask(0o077)
    if required_env("E2E_CONFIRM_ISOLATED") != "YES":
        raise RuntimeError("E2E_CONFIRM_ISOLATED must be exactly YES before any request")
    base_url = normalize_base_url(os.environ.get("E2E_BASE_URL", DEFAULT_BASE_URL))
    assert_isolated_target(base_url)
    admin_username = required_env("E2E_USERNAME").strip()
    admin_password = required_env("E2E_PASSWORD")
    validate_password("E2E_PASSWORD", admin_password)
    executable = browser_executable()

    run_tag = secrets.token_hex(4)
    staff_approved = make_account("工作人员批准", "STAFF", run_tag, 0)
    staff_rejected = make_account("工作人员驳回", "STAFF", run_tag, 1)
    resident_approved = make_account("居民批准", "RESIDENT", run_tag, 2)
    resident_rejected = make_account("居民驳回", "RESIDENT", run_tag, 3)
    id_suffix = secrets.randbelow(9_998)
    resident_approved["idCard"] = f"11010119900101{id_suffix:04d}"
    resident_rejected["idCard"] = f"11010119900101{id_suffix + 1:04d}"
    accounts = [staff_approved, staff_rejected, resident_approved, resident_rejected]

    playwright_runtime = sync_playwright().start()
    launch_options: dict[str, Any] = {"headless": True}
    if executable:
        launch_options["executable_path"] = executable
    try:
        try:
            browser = playwright_runtime.chromium.launch(**launch_options)
        except PlaywrightError as error:
            if executable is None:
                raise RuntimeError("No browser executable is available for registration UI E2E") from error
            raise

        guards: list[Any] = []
        admin_context = browser.new_context(viewport={"width": 1440, "height": 1000}, locale="zh-CN")
        admin_page = admin_context.new_page()
        guards.append(install_network_guard(admin_page, base_url))
        admin_audit = new_audit()
        attach_audit(admin_page, admin_audit)
        admin_page.set_default_timeout(DEFAULT_TIMEOUT_MS)
        admin_page.set_default_navigation_timeout(DEFAULT_TIMEOUT_MS)

        progress("logging in as administrator and creating isolated resident fixtures")
        login_admin(admin_page, base_url, admin_username, admin_password)
        grids = fetch_envelope(admin_page, "/api/grids?areaType=GRID&page=1&size=1")
        check(grids["status"] == 200 and grids["body"].get("code") == "OK", "Grid fixture lookup failed")
        grid_items = grids["body"]["data"]["items"]
        check(bool(grid_items), "Registration UI E2E requires one isolated grid fixture")
        grid_id = grid_items[0]["id"]

        resident_fixture_ids: list[str] = []
        for account in (resident_approved, resident_rejected):
            fixture = mutate_envelope(
                admin_page,
                "POST",
                "/api/residents",
                {
                    "gridId": grid_id,
                    "householdId": None,
                    "realName": account["realName"],
                    "gender": "UNKNOWN",
                    "birthDate": "1990-01-01",
                    "idCard": account["idCard"],
                    "phone": account["phone"],
                    "address": "隔离注册审核浏览器验收地址",
                    "isHouseholder": False,
                    "specialGroupTags": [],
                    "remark": "registration UI isolated fixture",
                },
            )
            check(fixture["status"] == 200 and fixture["body"].get("code") == "OK", "Resident fixture creation failed")
            resident_fixture_ids.append(fixture["body"]["data"]["id"])

        progress("submitting four applications through the public registration UI")
        for account in accounts:
            register_via_ui(browser, guards, base_url, account)

        progress("proving all four pending applications cannot log in")
        for account in accounts:
            attempt_login(browser, guards, base_url, account, None)

        review_requests: list[str] = []
        admin_page.on(
            "request",
            lambda request: review_requests.append(request.url)
            if "/registration-review" in request.url and request.method == "POST"
            else None,
        )
        progress("reviewing staff approval/rejection through the administrator UI")
        staff_approved_result = review_account(
            admin_page,
            base_url,
            staff_approved,
            "APPROVE",
            review_requests,
            test_cancel=True,
            test_empty=True,
            test_double_submit=True,
        )
        staff_rejected_result = review_account(
            admin_page,
            base_url,
            staff_rejected,
            "REJECT",
            review_requests,
            test_empty=True,
        )

        progress("reviewing resident approval/rejection through the administrator UI")
        resident_approved_result = review_account(
            admin_page, base_url, resident_approved, "APPROVE", review_requests
        )
        resident_rejected_result = review_account(
            admin_page, base_url, resident_rejected, "REJECT", review_requests
        )

        progress("proving approved home pages and rejected login denial")
        attempt_login(browser, guards, base_url, staff_approved, "/dashboard")
        attempt_login(browser, guards, base_url, resident_approved, "/resident/home")
        attempt_login(browser, guards, base_url, staff_rejected, None)
        attempt_login(browser, guards, base_url, resident_rejected, None)
        check(len(review_requests) == 4, "Expected exactly four registration review requests")
        assert_audit_clean(admin_audit, "administrator review")

        result = {
            "ok": True,
            "applications": 4,
            "pendingLoginDenied": 4,
            "reviewRequests": len(review_requests),
            "duplicateReviewRequests": 0,
            "approvedHomes": ["/dashboard", "/resident/home"],
            "staffApprovedUserId": staff_approved_result["id"],
            "staffRejectedUserId": staff_rejected_result["id"],
            "residentApprovedUserId": resident_approved_result["id"],
            "residentRejectedUserId": resident_rejected_result["id"],
            "approvedResidentFixtureId": resident_fixture_ids[0],
            "rejectedResidentFixtureId": resident_fixture_ids[1],
        }
        serialized_result = json.dumps(result, ensure_ascii=False, separators=(",", ":"))
        sensitive_values = {
            value
            for account in accounts
            for value in (account["username"], account["password"], account["phone"], account["idCard"])
            if value
        }
        check(
            all(value not in serialized_result for value in sensitive_values),
            "Registration E2E result must not expose generated account data",
        )
        print("REGISTRATION_E2E_RESULT " + serialized_result)
        admin_context.close()
        browser.close()
        return 0
    finally:
        playwright_runtime.stop()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # noqa: BLE001 - CLI boundary normalizes all failures.
        print(json.dumps({"ok": False, "error": str(error)}, ensure_ascii=False), file=sys.stderr)
        raise SystemExit(1)
