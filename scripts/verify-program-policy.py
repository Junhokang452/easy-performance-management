#!/usr/bin/env python3
"""Policy acceptance checks against the isolated synthetic local demo only."""
import argparse
import datetime as dt
import http.cookiejar
import json
import pathlib
import urllib.error
import urllib.parse
import urllib.request
import uuid

from local_tenant_fixture import tenant_b_headers

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:8087')
parser.add_argument('--output', default='_workspace/full-cycle-20260907/program-policy-result.json')
args = parser.parse_args()
if urllib.parse.urlsplit(args.base_url).hostname not in ('localhost', '127.0.0.1', '::1'):
    raise SystemExit('Only a synthetic local runtime is allowed')

PROGRAMS = '/api/v1/evaluation-programs'
RESOURCES = '/api/v1/evaluation-resources'
results = []


def check(label, condition):
    results.append({'case': label, 'passed': bool(condition)})
    if not condition:
        raise AssertionError(label)


class Actor:
    def __init__(self, name):
        self.name = name
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.call('POST', '/api/auth/session/login', {'email': f'dev-{name}@performance.dev', 'password': 'dev'})
        self.me = self.call('GET', '/api/v1/evaluation-workspace/me')

    def call(self, method, path, body=None, expected=200, label=None, binary=False, content_type=None, extra_headers=None):
        headers = {'X-Requested-With': 'XMLHttpRequest', **(extra_headers or {})}
        if isinstance(body, bytes):
            headers['Content-Type'] = content_type or 'application/octet-stream'
        elif body is not None:
            body = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        request = urllib.request.Request(args.base_url + urllib.parse.quote(path, safe='/?=&%,:'), data=body,
                                         method=method, headers=headers)
        try:
            with self.http.open(request, timeout=30) as response:
                status, raw, response_headers = response.status, response.read(), dict(response.headers)
        except urllib.error.HTTPError as error:
            status, raw, response_headers = error.code, error.read(), dict(error.headers)
        allowed = expected if isinstance(expected, tuple) else (expected,)
        label = label or f'{self.name} {method} {path}'
        results.append({'case': label, 'passed': status in allowed, 'status': status})
        if status not in allowed:
            raise AssertionError(f'{label}: expected {allowed}, got {status}: {raw[:500]!r}')
        if binary:
            return raw, {key.lower(): value for key, value in response_headers.items()}
        return json.loads(raw) if raw else None

    def upload(self, path, content, filename, mime, expected=200):
        boundary = 'EasyPolicy' + uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                f'Content-Type: {mime}\r\n\r\n').encode() + content + f'\r\n--{boundary}--\r\n'.encode()
        return self.call('POST', path, body, expected=expected,
                         content_type='multipart/form-data; boundary=' + boundary)


def verify_condition_values(hr):
    selected = {}
    for field in ('ORG_UNIT', 'POSITION', 'GRADE', 'JOB', 'EMPLOYMENT_TYPE', 'EMPLOYEE'):
        options = hr.call('GET', f'{RESOURCES}/lookup/condition-values?field={field}')
        check(f'{field} condition search returns 1..50 tenant options', 0 < len(options) <= 50)
        check(f'{field} options have stable values and human labels', all(o.get('value') and o.get('label') for o in options))
        option = options[0]
        exact = hr.call('GET', f'{RESOURCES}/lookup/condition-values?field={field}&q=impossible-query&values={option["value"]}')
        check(f'{field} selected value is restored independently from search query',
              len(exact) == 1 and exact[0]['value'] == option['value'])
        selected[field] = option['value']
    comma = hr.call('GET', f'{RESOURCES}/lookup/condition-values?field=GRADE&values={selected["GRADE"]},{selected["GRADE"]}')
    check('Comma-separated selected values are de-duplicated', len(comma) == 1)
    hr.call('GET', f'{RESOURCES}/lookup/condition-values?field=SALARY', expected=422,
            label='Unsupported condition field is rejected')

    token = hr.call('POST', '/api/auth/login', {'email': 'dev-hr-admin@performance.dev', 'password': 'dev'})['accessToken']
    foreign = hr.call('GET', f'{RESOURCES}/lookup/condition-values?field=EMPLOYEE&values={selected["EMPLOYEE"]}',
                      extra_headers=tenant_b_headers(token))
    check('Tenant B cannot restore a tenant A selected employee', not foreign)


def program_request(name):
    return {'name': name, 'evaluationYear': 2026, 'asOfDate': '2026-01-01',
            'startsOn': '2026-01-01', 'endsOn': '2026-12-31', 'kind': 'PERFORMANCE'}


def verify_multiple_assignments(hr, employee):
    employee_id = employee.me['employeeId']
    assignment_options = hr.call('GET', f'{RESOURCES}/lookup/employees/{employee_id}/assignments')
    check('Synthetic employee exposes at least two selectable assignments', len(assignment_options) >= 2)
    check('Assignment options never fall back to raw UUID labels',
          all(option['label'] and option['label'] != option['value'] for option in assignment_options))
    first_assignment, second_assignment = assignment_options[0]['value'], assignment_options[1]['value']
    stamp = dt.datetime.now().strftime('%Y%m%d%H%M%S')
    program = hr.call('POST', PROGRAMS, program_request('다중 발령 정책 검증 ' + stamp), expected=(200, 201))
    program_path = PROGRAMS + '/' + program['id']

    first = hr.call('POST', program_path + '/participants',
                    {'employeeId': employee_id, 'assignmentId': first_assignment, 'weightPercent': 40}, expected=(200, 201))
    hr.call('POST', program_path + '/participants',
            {'employeeId': employee_id, 'assignmentId': second_assignment, 'weightPercent': 70}, expected=422,
            label='Participant weights above 100 are rejected transactionally')
    after_failed_insert = hr.call('GET', program_path + '/participants')['content']
    check('Rejected second assignment insert leaves no partial participant',
          len(after_failed_insert) == 1 and after_failed_insert[0]['id'] == first['id'] and after_failed_insert[0]['weightPercent'] == 40)

    second = hr.call('POST', program_path + '/participants',
                     {'employeeId': employee_id, 'assignmentId': second_assignment, 'weightPercent': 60}, expected=(200, 201))
    hr.call('POST', program_path + '/participants',
            {'employeeId': employee_id, 'assignmentId': first_assignment, 'weightPercent': 50}, expected=422,
            label='Overweight update of an existing assignment is rolled back')
    after_failed_update = hr.call('GET', program_path + '/participants')['content']
    weight_by_id = {row['id']: row['weightPercent'] for row in after_failed_update}
    check('Rejected existing participant update preserves prior 40/60 weights',
          weight_by_id == {first['id']: 40, second['id']: 60})

    exported, headers = hr.call('GET', program_path + '/participants.xlsx', binary=True)
    check('Participant export is a real XLSX attachment', exported[:2] == b'PK' and 'attachment' in headers.get('content-disposition', ''))
    copied = hr.call('POST', program_path + '/copy',
                     {'name': '다중 발령 가져오기 검증 ' + stamp, 'evaluationYear': 2026, 'asOfDate': '2026-01-01',
                      'startsOn': '2026-01-01', 'endsOn': '2026-12-31'}, expected=201)
    copied_path = PROGRAMS + '/' + copied['id']
    imported = hr.upload(copied_path + '/participants:import', exported, 'participants.xlsx',
                         'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
    check('Participant XLSX roundtrip recreates both assignment rows', len(imported) == 2)
    copied_rows = hr.call('GET', copied_path + '/participants')['content']
    check('Roundtripped roster preserves assignment IDs and total weight',
          {row['employee']['assignmentId'] for row in copied_rows} == {first_assignment, second_assignment}
          and sum(row['weightPercent'] for row in copied_rows) == 100)

    hr.call('POST', program_path + '/common-items:apply', {})
    hr.call('POST', program_path + '/open', {})
    mine = employee.call('GET', program_path + '/me/participants')
    check('Employee can enumerate both of their own active participation contexts',
          {row['id'] for row in mine} == {first['id'], second['id']})
    employee.call('GET', program_path + '/me', expected=422,
                  label='Ambiguous member workspace requires participantId')
    for participant_id in (first['id'], second['id']):
        workspace = employee.call('GET', program_path + f'/me?participantId={participant_id}')
        check('Explicit participantId restores the intended member workspace', workspace['participant']['id'] == participant_id)
    return {'programId': program['id'], 'copyProgramId': copied['id'],
            'participantIds': [first['id'], second['id']]}


def run():
    hr = Actor('hr-admin')
    employee = Actor('employee')
    verify_condition_values(hr)
    return verify_multiple_assignments(hr, employee)


try:
    fixture = run()
except Exception as error:
    pathlib.Path(args.output).write_text(json.dumps({'passed': False, 'error': str(error), 'checks': results}, ensure_ascii=False, indent=2))
    raise
else:
    pathlib.Path(args.output).write_text(json.dumps({'passed': True, 'count': len(results), 'fixtures': fixture,
                                                    'checks': results}, ensure_ascii=False, indent=2))
    print(f'Program policy HTTP acceptance: {len(results)} checks passed')
