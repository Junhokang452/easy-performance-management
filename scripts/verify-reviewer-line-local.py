#!/usr/bin/env python3
"""S1 synthetic local API smoke; refuses non-loopback destinations. No live credentials."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import hmac
import http.cookiejar
import json
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:8089')
parser.add_argument('--output', required=True)
args = parser.parse_args()
if urllib.parse.urlsplit(args.base_url).hostname not in ('127.0.0.1', 'localhost', '::1'):
    raise SystemExit('Synthetic local runtime only')
TENANT = '00000000-0000-0000-0000-000000000001'
SECRET = 's1-synthetic-local-hmac-key-at-least-32-characters'
results = []
report = {'synthetic': True, 'baseUrl': args.base_url, 'cases': results}

def check(name, valid):
    results.append({'case': name, 'passed': bool(valid)})
    if not valid:
        raise AssertionError(name)

def call(opener, method, path, body=None, expected=200, headers=None, raw=False):
    data = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode('utf-8') if body is not None else None
    req = urllib.request.Request(args.base_url + path, data=data, method=method,
        headers={'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest', **(headers or {})})
    try:
        with opener.open(req, timeout=45) as response:
            status, content = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, content = error.code, error.read()
    allowed = (expected,) if isinstance(expected, int) else expected
    if status not in allowed:
        raise AssertionError(f'{method} {path}: wanted {allowed}, got {status}: {content[:200]!r}')
    return content if raw else json.loads(content) if content else None

def actor(name):
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    call(opener, 'POST', '/api/auth/session/login', {'email': f'dev-{name}@performance.dev', 'password': 'dev'})
    return opener

network = urllib.request.build_opener()

def sync(employees=None, assignments=None, tenant=TENANT, header_tenant=TENANT, expected=200):
    body = {'employees': employees or [], 'orgUnits': [], 'assignments': assignments or []}
    if tenant is not None:
        body['tenantId'] = tenant
    raw = json.dumps(body, ensure_ascii=False).encode('utf-8')
    headers = {'Authorization': 'Bearer s1-synthetic-local-token', 'X-Tenant-Uuid': header_tenant,
               'X-Signature': hmac.new(SECRET.encode(), raw, hashlib.sha256).hexdigest()}
    return call(network, 'POST', '/api/internal/sync/core-master', raw, expected, headers)

def create_program(hr, name, employee_ids):
    program = call(hr, 'POST', '/api/v1/evaluation-programs', {'name': name,
        'evaluationYear': 2026, 'asOfDate': '2026-01-01', 'startsOn': '2026-01-01',
        'endsOn': '2026-12-31', 'kind': 'PERFORMANCE'}, 201)
    participants = call(hr, 'POST', f'/api/v1/evaluation-programs/{program["id"]}/participants:generate', {'employeeIds': employee_ids})
    return program, participants

try:
    hr, employee_actor = actor('hr-admin'), actor('employee')
    version = time.time_ns() // 1000
    manager = str(uuid.uuid4())
    employee_ids = [str(uuid.uuid4()) for _ in range(4)]
    assignment_ids = [str(uuid.uuid4()) for _ in employee_ids]
    employees = [{'id': ident, 'employeeNo': 'S1-' + ident[:8], 'name': name, 'status': 'ACTIVE',
        'orgUnitId': None, 'employmentType': 'REGULAR', 'sourceVersion': version}
        for ident, name in zip([manager] + employee_ids, ['상사 시연', '자동배정 시연', '수동보존 시연', '원본변경 시연', '구계약 시연'])]
    assignments = [{'id': aid, 'employeeId': eid, 'orgUnitId': None, 'positionCode': 'MEMBER',
        'gradeCode': None, 'jobCode': None, 'effectiveFrom': '2026-01-01', 'effectiveTo': '2026-12-31',
        'sourceVersion': version, 'managerEmployeeId': manager, 'deleted': False}
        for aid, eid in zip(assignment_ids, employee_ids)]
    receipt = sync(employees, assignments)
    check('authenticated UTF-8 source applied', receipt['employeesApplied'] == 5 and receipt['assignmentsApplied'] == 4)
    receipt = sync(employees, assignments)
    check('source replay skips duplicates', receipt['assignmentsSkipped'] == 4)
    sync(tenant='00000000-0000-0000-0000-000000000002', expected=(401, 403))
    check('signed tenant mismatch rejected', True)
    sync(header_tenant='00000000-0000-0000-0000-000000000002', expected=(401, 403))
    check('header tenant mismatch rejected', True)
    sync(tenant=None, expected=401)
    check('legacy body cannot establish a tenant context', True)

    legacy = dict(assignments[3]); legacy.pop('deleted'); legacy.pop('managerEmployeeId'); legacy['sourceVersion'] += 1
    sync(assignments=[legacy])
    program, participants = create_program(hr, 'S1 API synthetic ' + str(version), employee_ids)
    pid = program['id']; ids = [p['id'] for p in participants]
    by_employee = {p['employee']['employeeId']: p['id'] for p in participants}
    ids = [by_employee[eid] for eid in employee_ids]
    preview_url = f'/api/v1/evaluation-programs/{pid}/reviewer-line:preview'
    apply_url = f'/api/v1/evaluation-programs/{pid}/reviewer-line:apply'
    request = {'participantIds': ids, 'roles': ['REVIEWER']}
    before = call(hr, 'POST', preview_url, request)
    check('preview ready plus legacy blocker', before['summary']['ready'] == 3 and before['summary']['blocked'] == 1)
    check('inclusive as-of start date', before['asOfDate'] == '2026-01-01')
    reviewers_url = f'/api/v1/evaluation-programs/participants/{ids[1]}/reviewers'
    manual = call(hr, 'PUT', reviewers_url, {'reviewers': [{'employeeId': manager, 'role': 'REVIEWER', 'round': 1, 'weightPercent': 100}]})
    fresh = call(hr, 'POST', preview_url, request)
    check('existing manual participant skipped', fresh['summary']['skippedExisting'] == 1)
    apply_request = {**request, 'previewHash': fresh['previewHash'], 'reason': 'synthetic local S1 verification'}
    with ThreadPoolExecutor(max_workers=2) as pool:
        attempts = [pool.submit(call, hr, 'POST', apply_url, apply_request) for _ in range(2)]
        applied, concurrent = [attempt.result() for attempt in attempts]
    check('concurrent apply shares one persisted run', applied == concurrent)
    check('only ready participants applied', applied['applied'] == 2 and applied['skipped'] == 2)
    replay = call(hr, 'POST', apply_url, apply_request)
    check('same run replay is idempotent', replay == applied)
    check('manual reviewer unchanged', call(hr, 'GET', reviewers_url) == manual)
    for index in (0, 2):
        rows = call(hr, 'GET', f'/api/v1/evaluation-programs/participants/{ids[index]}/reviewers')
        check(f'concurrent apply creates one reviewer for participant {index}', len(rows) == 1)
    call(employee_actor, 'POST', preview_url, request, 403)
    check('employee preview forbidden', True)
    call(employee_actor, 'POST', apply_url, apply_request, 403)
    check('employee apply forbidden', True)
    call(hr, 'POST', preview_url, {'participantIds': [ids[0]], 'roles': ['ADJUSTER']}, 422)
    check('HR adjuster is not inferred', True)
    call(hr, 'POST', apply_url, {**apply_request, 'participantIds': ids * 26}, 422)
    check('apply rejects oversized selection', True)
    call(hr, 'POST', preview_url, {'participantIds': [ids[0], ids[0]], 'roles': ['REVIEWER']}, 422)
    check('duplicate participant IDs rejected', True)
    other_program, other_participants = create_program(hr, 'S1 stale synthetic ' + str(version), [employee_ids[2]])
    other_pid = other_program['id']; other_id = other_participants[0]['id']
    other_request = {'participantIds': [other_id], 'roles': ['REVIEWER']}
    stale = call(hr, 'POST', f'/api/v1/evaluation-programs/{other_pid}/reviewer-line:preview', other_request)
    deleted = {**assignments[2], 'deleted': True, 'sourceVersion': version + 2}
    sync(assignments=[deleted])
    call(hr, 'POST', f'/api/v1/evaluation-programs/{other_pid}/reviewer-line:apply',
        {**other_request, 'previewHash': stale['previewHash'], 'reason': 'stale must reject'}, 409)
    check('source tombstone invalidates preview', True)
    call(hr, 'POST', preview_url, {'participantIds': [other_id], 'roles': ['REVIEWER']}, 404)
    check('cross-program participant rejected', True)
    xlsx = call(hr, 'GET', f'/api/v1/evaluation-programs/{pid}/reviewers.xlsx', raw=True)
    check('reviewer Excel export preserved', xlsx.startswith(b'PK'))

    # Leave a separate unapplied synthetic program for the browser confirmation flow.
    browser_program, browser_participants = create_program(hr, 'S1 browser synthetic ' + str(version), [employee_ids[0], employee_ids[3]])
    report.update(programId=pid, browserProgramId=browser_program['id'], browserParticipants=browser_participants,
                  completed=True)
except Exception as error:
    report.update(completed=False, error=str(error))
    raise
finally:
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'passed': sum(x['passed'] for x in results), 'total': len(results), 'completed': report.get('completed')}, ensure_ascii=False))
