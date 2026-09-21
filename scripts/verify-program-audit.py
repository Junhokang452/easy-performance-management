#!/usr/bin/env python3
"""Read-only HTTP verification for the operator audit projection on the local demo."""

import argparse
import base64
import hashlib
import hmac
import http.cookiejar
import json
import os
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request

from local_tenant_fixture import tenant_b_headers


parser = argparse.ArgumentParser()
parser.add_argument("--base-url", default="http://127.0.0.1:8087")
parser.add_argument(
    "--output",
    default="_workspace/5240-evaluation-20260908/backend-audit-api.json",
)
args = parser.parse_args()
if urllib.parse.urlsplit(args.base_url).hostname not in ("localhost", "127.0.0.1", "::1"):
    raise SystemExit("Only a synthetic local runtime is allowed")

results = []
fixture_coverage = {}
EXPECTED_ROW_FIELDS = {
    "id",
    "participantId",
    "eventType",
    "reason",
    "actorEmployeeId",
    "createdAt",
}


def record(label, passed, **details):
    result = {"case": label, "passed": bool(passed), **details}
    results.append(result)
    if not passed:
        raise AssertionError(label)


class Actor:
    def __init__(self, name):
        self.name = name
        self.http = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )
        self.call(
            "POST",
            "/api/auth/session/login",
            {"email": f"dev-{name}@performance.dev", "password": "dev"},
        )

    def call(self, method, path, body=None, expected=200, headers=None):
        request_headers = {"X-Requested-With": "XMLHttpRequest", **(headers or {})}
        encoded = None
        if body is not None:
            encoded = json.dumps(body).encode()
            request_headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            args.base_url + urllib.parse.quote(path, safe="/?=&%,"),
            data=encoded,
            method=method,
            headers=request_headers,
        )
        try:
            with self.http.open(request, timeout=30) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        allowed = expected if isinstance(expected, tuple) else (expected,)
        if status not in allowed:
            raise AssertionError(
                f"{self.name} {method} {path}: expected {allowed}, got {status}: {raw[:300]!r}"
            )
        try:
            payload = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            payload = raw.decode(errors="replace")
        return status, payload


def projected_only(node):
    forbidden = {"detailsJson", "tenantId"}
    if isinstance(node, dict):
        return not any(key in forbidden for key in node) and all(
            projected_only(value) for value in node.values()
        )
    if isinstance(node, list):
        return all(projected_only(value) for value in node)
    return True


def foreign_tenant_headers(token, subject="019ed004-0000-7000-8000-000000000002"):
    """Use an ephemeral Windows verification secret when supplied, otherwise local-demo's fixture."""
    secret = os.environ.get("PERFORMANCE_DEMO_JWT_SECRET")
    if not secret:
        return tenant_b_headers(token)
    header, payload, _ = token.split(".")
    claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    claims.update(
        sub=subject,
        tid="00000000-0000-0000-0000-000000000002",
    )
    payload = base64.urlsafe_b64encode(
        json.dumps(claims, separators=(",", ":")).encode()
    ).rstrip(b"=").decode()
    content = header + "." + payload
    signature = base64.urlsafe_b64encode(
        hmac.new(secret.encode(), content.encode(), hashlib.sha512).digest()
    ).rstrip(b"=").decode()
    return {"Authorization": "Bearer " + content + "." + signature}


def cookie_free_get(path, headers, expected=200):
    request = urllib.request.Request(
        args.base_url + urllib.parse.quote(path, safe="/?=&%,"),
        method="GET",
        headers=headers,
    )
    opener = urllib.request.build_opener()
    try:
        with opener.open(request, timeout=30) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    allowed = expected if isinstance(expected, tuple) else (expected,)
    if status not in allowed:
        raise AssertionError(
            f"cookie-free GET {path}: expected {allowed}, got {status}: {raw[:300]!r}"
        )
    try:
        payload = json.loads(raw) if raw else None
    except json.JSONDecodeError:
        payload = raw.decode(errors="replace")
    return status, payload


def run():
    hr = Actor("hr-admin")
    employee = Actor("employee")
    _, programs = hr.call("GET", "/api/v1/evaluation-programs?page=0&size=100")
    record("Program list uses the expected page envelope", isinstance(programs.get("content"), list))

    selected = None
    history = None
    for program in programs["content"]:
        path = f"/api/v1/evaluation-programs/{program['id']}/audit-events?page=0&size=100"
        _, candidate = hr.call("GET", path)
        if candidate.get("content"):
            selected, history = program, candidate
            break
    record("Operator can read an existing program audit history", selected is not None)

    base = f"/api/v1/evaluation-programs/{selected['id']}/audit-events"
    rows = history["content"]
    record(
        "Every audit row has the exact six-field response contract",
        all(set(row) == EXPECTED_ROW_FIELDS for row in rows),
    )
    record("Audit projection never exposes detailsJson or tenantId", projected_only(history))
    record(
        "Audit page is stably newest-first",
        all(
            (rows[index]["createdAt"], rows[index]["id"])
            >= (rows[index + 1]["createdAt"], rows[index + 1]["id"])
            for index in range(len(rows) - 1)
        ),
    )

    _, first_page = hr.call("GET", base + "?page=0&size=1")
    _, second_page = hr.call("GET", base + "?page=1&size=1")
    record(
        "Page metadata exposes stable totalPages, number and size fields",
        all(key in first_page for key in ("totalElements", "totalPages", "number", "size"))
        and first_page["number"] == 0
        and first_page["size"] == 1
        and first_page["totalPages"] >= 2,
    )
    record(
        "Pagination returns distinct adjacent rows",
        first_page["content"]
        and second_page["content"]
        and first_page["content"][0]["id"] != second_page["content"][0]["id"],
    )

    filtered_seed = next((row for row in rows if row.get("participantId")), None)
    record("Audit fixture contains a participant-scoped event", filtered_seed is not None)
    event_query = urllib.parse.urlencode(
        {"eventType": filtered_seed["eventType"], "page": 0, "size": 100}
    )
    _, event_filtered = hr.call("GET", base + "?" + event_query)
    record(
        "eventType works as an independent filter",
        bool(event_filtered["content"])
        and all(
            row["eventType"] == filtered_seed["eventType"]
            for row in event_filtered["content"]
        ),
    )
    participant_query = urllib.parse.urlencode(
        {"participantId": filtered_seed["participantId"], "page": 0, "size": 100}
    )
    _, participant_filtered = hr.call("GET", base + "?" + participant_query)
    record(
        "participantId works as an independent filter",
        bool(participant_filtered["content"])
        and all(
            row["participantId"] == filtered_seed["participantId"]
            for row in participant_filtered["content"]
        ),
    )
    query = urllib.parse.urlencode(
        {
            "eventType": filtered_seed["eventType"],
            "participantId": filtered_seed["participantId"],
            "page": 0,
            "size": 100,
        }
    )
    _, filtered = hr.call("GET", base + "?" + query)
    record(
        "eventType and participantId compose as tenant-scoped filters",
        bool(filtered["content"])
        and all(
            row["eventType"] == filtered_seed["eventType"]
            and row["participantId"] == filtered_seed["participantId"]
            for row in filtered["content"]
        ),
    )

    fixture_coverage["nullParticipant"] = (
        "verified" if any(row.get("participantId") is None for row in rows)
        else "unverified-no-fixture"
    )
    fixture_coverage["nullActor"] = (
        "verified" if any(row.get("actorEmployeeId") is None for row in rows)
        else "unverified-no-fixture"
    )

    employee_status, _ = employee.call("GET", base, expected=403)
    record("Employee audit access is forbidden", employee_status == 403, status=employee_status)

    _, raw_tokens = hr.call(
        "POST",
        "/api/auth/login",
        {"email": "dev-hr-admin@performance.dev", "password": "dev"},
    )
    foreign_headers = foreign_tenant_headers(raw_tokens["accessToken"])
    foreign_session_status, foreign_session = cookie_free_get(
        "/api/auth/session", foreign_headers
    )
    record(
        "Tenant B active operator has a valid cookie-free session",
        foreign_session_status == 200
        and foreign_session["tenantId"] == "00000000-0000-0000-0000-000000000002"
        and "HR_ADMIN" in foreign_session["roles"],
        status=foreign_session_status,
    )
    foreign_program_status, foreign_programs = cookie_free_get(
        "/api/v1/evaluation-programs?page=0&size=100", foreign_headers
    )
    record(
        "Tenant B active operator can read its own program collection",
        foreign_program_status == 200 and isinstance(foreign_programs.get("content"), list),
        status=foreign_program_status,
    )
    invalid_headers = foreign_tenant_headers(
        raw_tokens["accessToken"], "019ed004-0000-7000-8000-000000000099"
    )
    invalid_session_status, _ = cookie_free_get(
        "/api/auth/session", invalid_headers, expected=(401, 403, 404)
    )
    record(
        "A signed but nonexistent Tenant B actor is denied",
        invalid_session_status in (401, 403, 404),
        status=invalid_session_status,
    )
    foreign_status, _ = cookie_free_get(
        base, foreign_headers, expected=(403, 404)
    )
    record(
        "Tenant B cannot read tenant A audit history",
        foreign_status in (403, 404),
        status=foreign_status,
    )

    invalid_status, _ = hr.call("GET", base + "?page=0&size=101", expected=422)
    record(
        "Page size above 100 is rejected",
        invalid_status == 422,
        status=invalid_status,
    )


output = pathlib.Path(args.output)
try:
    run()
    passed = True
    error = None
except Exception as exception:  # evidence must survive the first failing assertion
    passed = False
    if isinstance(exception, urllib.error.URLError):
        error = "URLError: local runtime unavailable"
    else:
        error = f"{type(exception).__name__}: {exception}"
finally:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            {
                "passed": passed,
                "checks": len(results),
                "results": results,
                "error": error,
                "baseUrl": args.base_url,
                "fixtureCoverage": fixture_coverage,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n"
    )

if not passed:
    print(error, file=sys.stderr)
    raise SystemExit(1)
print(f"Program audit HTTP acceptance: {len(results)} checks passed")
