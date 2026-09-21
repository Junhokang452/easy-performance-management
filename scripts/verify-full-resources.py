#!/usr/bin/env python3
"""Real HTTP acceptance checks against the isolated synthetic local demo only."""
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
parser.add_argument('--output', default='_workspace/full-cycle-20260907/resources-api-result.json')
args = parser.parse_args()
if urllib.parse.urlsplit(args.base_url).hostname not in ('localhost', '127.0.0.1', '::1'):
    raise SystemExit('Only a synthetic local runtime is allowed')
BASE = '/api/v1/evaluation-resources'
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

    def call(self, method, path, body=None, expected=200, label=None, content_type=None, binary=False, extra_headers=None):
        headers = {'X-Requested-With': 'XMLHttpRequest', **(extra_headers or {})}
        if isinstance(body, bytes):
            headers['Content-Type'] = content_type or 'application/octet-stream'
        elif body is not None:
            body = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(args.base_url + urllib.parse.quote(path, safe='/?=&%'), data=body, method=method, headers=headers)
        try:
            with self.http.open(req, timeout=30) as response:
                status, raw, rh = response.status, response.read(), dict(response.headers)
        except urllib.error.HTTPError as error:
            status, raw, rh = error.code, error.read(), dict(error.headers)
        allowed = expected if isinstance(expected, tuple) else (expected,)
        label = label or f'{self.name} {method} {path}'
        results.append({'case': label, 'passed': status in allowed, 'status': status})
        if status not in allowed:
            raise AssertionError(f'{label}: expected {allowed}, got {status}: {raw[:500]!r}')
        if binary:
            return raw, {k.lower(): v for k, v in rh.items()}
        return json.loads(raw) if raw else None

    def upload(self, path, content, filename='evidence.txt', mime='text/plain', expected=200):
        boundary = 'EasySynthetic' + uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n').encode() + content + f'\r\n--{boundary}--\r\n'.encode()
        return self.call('POST', path, body, expected, content_type='multipart/form-data; boundary=' + boundary)


def run():
    hr, employee, manager, colleague = [Actor(n) for n in ('hr-admin', 'employee', 'manager', 'colleague')]
    eid, mid, cid = [a.me['employeeId'] for a in (employee, manager, colleague)]
    stamp = dt.datetime.now().strftime('%Y%m%d%H%M%S')
    catalog_body = {'kind': 'PERFORMANCE', 'category': '고객 서비스', 'name': '응답 품질 ' + stamp,
                    'definition': '고객 문의를 정확하게 해결합니다.', 'active': True, 'displayOrder': 1,
                    'achievementLevels': [{'code': 'A', 'label': '목표 달성', 'minValue': 80, 'maxValue': 100, 'description': '목표 충족', 'displayOrder': 0}], 'assignments': []}
    employee.call('POST', BASE + '/catalogs', catalog_body, expected=403, label='Member cannot change shared evaluation definitions')
    catalog = hr.call('POST', BASE + '/catalogs', catalog_body)
    got = employee.call('GET', BASE + '/catalogs/' + catalog['id'])
    check('Member sees registered catalog achievement levels', got['achievementLevels'][0]['code'] == 'A')
    copied = hr.call('POST', BASE + '/catalogs/' + catalog['id'] + '/copy', {'name': '역량 복사 검증 ' + stamp})
    check('Catalog copy preserves source provenance', copied['copiedFromId'] == catalog['id'])
    departments = hr.call('GET', BASE + '/lookup/departments')
    check('Department lookup has real local read-model options', bool(departments))
    department_id = departments[0]['value']
    goal = hr.call('POST', BASE + '/department-goals', {'year': 2026, 'periodStart': '2026-01-01', 'periodEnd': '2026-12-31',
        'departmentId': department_id, 'catalogId': catalog['id'], 'title': '부서 고객 서비스 ' + stamp,
        'definition': '부서 목표와 성과 과제 연결', 'weight': 100, 'targetLevel': 'A', 'unit': '건'})
    actual = hr.call('POST', BASE + '/department-goals/' + goal['id'] + '/actual', {'actualValue': 90, 'achievementRate': 90, 'note': '분기 증빙 90건'})
    check('Department achievement is persisted', actual['actualValue'] == 90 and actual['achievementRate'] == 90)
    task_body = {'title': '체크리스트 과제 ' + stamp, 'periodStart': '2026-01-01', 'periodEnd': '2026-12-31',
                 'description': '단계별 진행 증빙', 'progressMode': 'CHECKLIST', 'departmentGoalId': None,
                 'stakeholders': [{'employeeId': eid, 'role': 'OWNER'}, {'employeeId': mid, 'role': 'MANAGER'}]}
    task = employee.call('POST', BASE + '/tasks', task_body)
    tp = BASE + '/tasks/' + task['task']['id']
    employee.call('PUT', tp, {k: v for k, v in {**task_body, 'title': '수정된 체크리스트 과제 ' + stamp}.items() if k != 'progressMode'}, label='Task update replaces stakeholder list without unique constraint conflict')
    colleague.call('GET', tp, expected=(403, 404), label='Unrelated employee cannot read private task')
    colleague.call('GET', BASE + '/tasks')
    check('Private task absent from unrelated employee list', not any(t['id'] == task['task']['id'] for t in colleague.call('GET', BASE + '/tasks')))
    employee.call('POST', tp + '/status', {'status': 'IN_PROGRESS', 'reason': '착수'})
    one = employee.call('POST', tp + '/checklist', {'text': '사례 수집', 'displayOrder': 0})
    two = employee.call('POST', tp + '/checklist', {'text': '응답 개선', 'displayOrder': 1})
    employee.call('PATCH', tp + '/checklist/' + one['id'], {'completed': True})
    check('One of two completed checklist entries yields 50 percent', employee.call('GET', tp)['task']['progressPercent'] == 50)
    employee.call('POST', tp + '/progress', {'progressPercent': 99, 'note': '수동 변경 시도'}, expected=(409, 422), label='Checklist percentage cannot be overwritten manually')
    employee.call('POST', tp + '/activities', {'message': '자료를 수집하고 개선 방안을 정리했습니다.'})
    label = employee.call('POST', BASE + '/task-labels', {'name': '검증-' + stamp})
    employee.call('POST', tp + '/labels', {'labelId': label['id']})
    feedback = manager.call('POST', tp + '/feedback', {'toEmployeeId': eid, 'rating': 5, 'message': '사례 정리가 구체적입니다.'})
    check('Stakeholder feedback is stored as evidence rather than calculated evaluation score', feedback['rating'] == 5)
    evidence = 'Synthetic evidence only — 합성 증빙'.encode() + b'x' * (1024 * 1024 + 1)
    employee.upload(tp + '/attachments', b'x' * (10 * 1024 * 1024 + 1), expected=422)
    attachment = employee.upload(tp + '/attachments', evidence)
    file_path = tp + '/attachments/' + attachment['id']
    downloaded, headers = manager.call('GET', file_path, binary=True)
    check('Authorized stakeholder downloads exact bytes', downloaded == evidence)
    check('Private file is attachment with nosniff', 'attachment' in headers.get('content-disposition', '') and headers.get('x-content-type-options') == 'nosniff')
    colleague.call('GET', file_path, expected=(403, 404), label='Unrelated employee cannot download task evidence')
    token = hr.call('POST', '/api/auth/login', {'email': 'dev-hr-admin@performance.dev', 'password': 'dev'})['accessToken']
    foreign_headers = tenant_b_headers(token)
    foreign_me = hr.call('GET', '/api/v1/evaluation-workspace/me', extra_headers=foreign_headers)
    check('Independent authenticated tenant B fixture is active', foreign_me['tenantId'] == '00000000-0000-0000-0000-000000000002')
    hr.call('GET', file_path, expected=(403, 404), extra_headers=foreign_headers, label='Tenant B operator cannot download tenant A task attachment')
    hr.call('GET', BASE + '/catalogs/' + catalog['id'], expected=(403, 404), extra_headers=foreign_headers, label='Tenant B cannot read tenant A catalog')
    employee.call('PATCH', tp + '/checklist/' + two['id'], {'completed': True})
    employee.call('POST', tp + '/status', {'status': 'COMPLETED', 'reason': '모든 항목 완료'})
    employee.call('POST', tp + '/activities', {'message': '완료 후 무단 변경'}, expected=(409, 422), label='Completed task protects work history')
    reopened = employee.call('POST', tp + '/reopen', {'reason': '추가 개선 작업'})
    check('Explicit reopen resumes completed task with recorded reason', reopened['task']['status'] == 'IN_PROGRESS')
    actual_task = employee.call('POST', BASE + '/tasks', {**task_body, 'title': '실적 과제 ' + stamp, 'progressMode': 'ACTUAL'})
    ap = BASE + '/tasks/' + actual_task['task']['id']
    employee.call('POST', ap + '/status', {'status': 'IN_PROGRESS', 'reason': '착수'})
    employee.call('POST', ap + '/progress', {'progressPercent': 65, 'note': '전체 100건 중 65건 완료'})
    check('Actual progress survives separate reload', employee.call('GET', ap)['task']['progressPercent'] == 65)
    employee.call('POST', ap + '/progress', {'progressPercent': 101, 'note': '범위 초과'}, expected=(400, 422), label='Out-of-range progress rejected')
    interview = manager.call('POST', BASE + '/interviews', {'subjectEmployeeId': eid,
        'occurredAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'summary': '개인적인 성장 면담 ' + stamp, 'keyIssues': '업무 숙련', 'requests': '사례 교육', 'followUp': '다음 분기 점검',
        'subjectVisible': False, 'referencesVisible': False, 'referenceEmployeeIds': [cid]})
    ip = BASE + '/interviews/' + interview['id']
    employee.call('GET', ip, expected=(403, 404), label='Subject cannot read interview until subject visibility enabled')
    colleague.call('GET', ip, expected=(403, 404), label='Reference cannot read interview until reference visibility enabled')
    hr.call('GET', ip, expected=(403, 404), label='HR title does not bypass private interview visibility')
    manager.call('PATCH', ip + '/visibility', {'subjectVisible': True, 'referencesVisible': False})
    check('Subject independently gains access', employee.call('GET', ip)['summary'] == interview['summary'])
    colleague.call('GET', ip, expected=(403, 404), label='Subject sharing does not grant reference access')
    manager.call('PATCH', ip + '/visibility', {'subjectVisible': False, 'referencesVisible': True})
    colleague.call('GET', ip)
    employee.call('GET', ip, expected=(403, 404), label='Revoking subject visibility applies immediately')
    employee.call('PUT', ip, {'occurredAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'summary': '타인 기록 수정'}, expected=(403, 404), label='Subject cannot edit author interview')
    check('Visibility changes leave audit history', len(manager.call('GET', ip)['auditTrail']) >= 3)
    check('Referenced view returns shared interview', any(i['id'] == interview['id'] for i in colleague.call('GET', BASE + '/interviews?view=REFERENCED')))
    return {'catalogId': catalog['id'], 'departmentGoalId': goal['id'], 'taskId': task['task']['id'], 'interviewId': interview['id']}


try:
    fixture = run()
except Exception as error:
    pathlib.Path(args.output).write_text(json.dumps({'passed': False, 'error': str(error), 'checks': results}, ensure_ascii=False, indent=2))
    raise
else:
    pathlib.Path(args.output).write_text(json.dumps({'passed': True, 'count': len(results), 'fixtures': fixture, 'checks': results}, ensure_ascii=False, indent=2))
    print(f'Resource HTTP acceptance: {len(results)} checks passed')
