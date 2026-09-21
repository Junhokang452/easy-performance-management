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
parser.add_argument('--output', default='_workspace/full-cycle-20260907/program-api-result.json')
args = parser.parse_args()
if urllib.parse.urlsplit(args.base_url).hostname not in ('localhost', '127.0.0.1', '::1'):
    raise SystemExit('Only a synthetic local runtime is allowed')
BASE = '/api/v1/evaluation-programs'
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


def check_no_scores(data, label):
    protected = {'score', 'rawScore', 'normalizedScore', 'adjustedScore', 'numericScore', 'beforeScore'}
    def leaked(node):
        if isinstance(node, dict):
            return any((key in protected and value is not None) or leaked(value) for key, value in node.items())
        if isinstance(node, list):
            return any(leaked(value) for value in node)
        return False
    check(label, not leaked(data))


def run():
    hr, employee, manager, director, colleague = [Actor(n) for n in ('hr-admin', 'employee', 'manager', 'director', 'colleague')]
    eid, mid, did, cid = [a.me['employeeId'] for a in (employee, manager, director, colleague)]
    stamp = dt.datetime.now().strftime('%Y%m%d%H%M%S')
    create = {'name': '다차수 전 과정 검증 ' + stamp, 'evaluationYear': 2026, 'asOfDate': '2026-01-01',
              'startsOn': '2026-01-01', 'endsOn': '2026-12-31', 'kind': 'PERFORMANCE'}
    employee.call('POST', BASE, create, expected=403, label='Member cannot create evaluation programs')
    program = hr.call('POST', BASE, create, expected=(200, 201))
    pp = BASE + '/' + program['id']
    token = hr.call('POST', '/api/auth/login', {'email': 'dev-hr-admin@performance.dev', 'password': 'dev'})['accessToken']
    foreign_headers = tenant_b_headers(token)
    hr.call('GET', pp, expected=(403, 404), extra_headers=foreign_headers, label='Tenant B operator cannot read tenant A program')
    config = program['configuration']
    # A known 0..100 input scale allows independent weighted-score expectations.
    input_id = config['calculation']['inputScaleId']
    for scale in config['scales']:
        if scale['id'] == input_id:
            scale['name'] = '0-100 점수'
            scale['kind'] = 'SCORE'
            scale['levels'] = [{'code': 'POINTS', 'label': '점수', 'convertedScore': 100,
                                'lowerExclusive': -1, 'upperInclusive': 100, 'color': None}]
    config['publication']['memberResultVisibility'] = 'GRADE_ONLY'
    group = config['groups'][0]
    group['reviewerWeightPlans'] = [
        {'actualReviewerCount': 1, 'reviewerWeights': {'1': 100}, 'departmentWeight': 0},
        {'actualReviewerCount': 2, 'reviewerWeights': {'1': 40, '2': 60}, 'departmentWeight': 0}]
    bad = json.loads(json.dumps(config)); bad['groups'][0]['reviewerWeightPlans'][1]['reviewerWeights']['2'] = 50
    hr.call('PUT', pp + '/definition', {'configuration': bad, 'revisionReason': '잘못된 가중합 검증'}, expected=(400, 422), label='Reviewer weights must total exactly 100')
    configured = hr.call('PUT', pp + '/definition', {'configuration': config, 'revisionReason': '2차 평가 구성'})
    check('Definition revision advances on actual edit', configured['definitionRevision'] > program['definitionRevision'])
    participants = []
    for employee_id in (eid, cid):
        participants.append(hr.call('POST', pp + '/participants', {'employeeId': employee_id, 'weightPercent': 100}, expected=(200, 201)))
    participant = participants[0]; pid = participant['id']; sp = BASE + '/participants/' + pid
    reviewers = [{'employeeId': mid, 'role': 'AGREEMENT_REVIEWER', 'round': 0, 'weightPercent': 0},
                 {'employeeId': mid, 'role': 'CHECKER', 'round': 0, 'weightPercent': 0},
                 {'employeeId': mid, 'role': 'REVIEWER', 'round': 1, 'weightPercent': 40},
                 {'employeeId': did, 'role': 'REVIEWER', 'round': 2, 'weightPercent': 60},
                 {'employeeId': did, 'role': 'ADJUSTER', 'round': 0, 'weightPercent': 0},
                 {'employeeId': did, 'role': 'FINAL_FEEDBACK', 'round': 0, 'weightPercent': 0}]
    hr.call('PUT', sp + '/reviewers', {'reviewers': reviewers})
    employee.call('GET', pp, expected=(403, 404), label='Draft definition is hidden from assigned employee')
    guide_bytes = '평가 기준과 일정 안내'.encode()
    guide = hr.upload(pp + '/guides', guide_bytes, expected=201)
    hr.call('POST', pp + '/common-items:apply', {})
    opened = hr.call('POST', pp + '/open', {})
    check('Program opening is distinct from participant stage execution', opened['status'] == 'OPEN')
    employee.call('GET', pp, expected=403, label='Full administrative definition stays operator-only')
    employee.call('GET', pp + '/me')
    colleague.call('GET', sp + '/goals', expected=(403, 404), label='Same program participant cannot read another employee goals')

    def start(stage, ids=None):
        return hr.call('POST', pp + '/stages/' + stage + ':start', {'participantIds': ids or [pid], 'reason': '검증 단계 진행'})

    start('GOAL')
    goal_body = {'catalogItemId': None, 'departmentGoalId': None, 'title': '고객 문의 응답 개선',
                 'definition': '고객 문의 100건 처리', 'weightPercent': 100, 'targetValue': 100, 'unit': '건',
                 'achievementLevels': [{'code': 'A', 'label': '목표 달성', 'thresholdValue': 100}]}
    goal = employee.call('POST', sp + '/goals', goal_body, expected=(200, 201)); gp = BASE + '/goals/' + goal['id']
    employee.call('POST', gp + ':request-agreement', {})
    employee.call('POST', gp + ':decide', {'approve': True, 'opinion': '자기 승인'}, expected=403, label='Goal author cannot approve own agreement')
    hr.call('PUT', gp + '/opinion', {'opinion': '관리자 대리 내용 수정'}, expected=403, label='Administrator preview cannot alter reviewer-authored content')
    manager.call('PUT', gp + '/opinion', {'opinion': '측정 기준을 보완해주세요.'})
    manager.call('POST', gp + ':decide', {'approve': False, 'opinion': '측정 기준 구체화'})
    employee.call('PUT', gp, {**goal_body, 'definition': '처리 완료 건수를 주별 집계합니다.'})
    employee.call('POST', gp + ':request-agreement', {})
    manager.call('POST', gp + ':decide', {'approve': True, 'opinion': '구체화된 목표에 합의합니다.'})
    check('Agreement history preserves rejection and subsequent approval', len(employee.call('GET', gp + '/history')) >= 4)
    batch = start('INTERMEDIATE', [p['id'] for p in participants])
    check('Batch transition advances ready employee and excludes unready employee independently', pid in batch['changedParticipantIds'] and any(x['participantId'] == participants[1]['id'] for x in batch['excluded']))
    manager.call('PUT', sp + '/intermediate', {'opinion': '실적 60건, 복잡한 사례 지원 필요', 'taskIds': []})
    manager.call('POST', sp + '/intermediate:complete', {'opinion': '지원 계획 합의 완료', 'taskIds': []})
    start('SELF_REVIEW')

    def submit(actor, round_number, score):
        context = actor.call('GET', sp + '/review-context?round=' + str(round_number))
        items = context['items'] or context['goals']
        check(actor.name + ' receives actual evaluable items', bool(items))
        answers = [{'itemId': i['id'], 'numericScore': score, 'scaleCode': None, 'opinion': '구체적인 업무 증빙을 확인했습니다.'} for i in items]
        rp = sp + '/review-submission'
        query = '?round=' + str(round_number)
        actor.call('PUT', rp + query, {'answers': answers, 'overallOpinion': '업무 성과와 개선 사항을 검토했습니다.'})
        done = actor.call('POST', rp + ':complete' + query, {'answers': answers, 'overallOpinion': '업무 성과와 개선 사항을 검토했습니다.'})
        check(actor.name + ' submission is locked when completed', done['status'] == 'COMPLETED')
        actor.call('PUT', rp + query, {'answers': answers, 'overallOpinion': '완료 후 변경'}, expected=(409, 422), label='Submitted round cannot be silently overwritten')
        return context

    submit(employee, 0, 85)
    start('REVIEW')
    director.call('PUT', sp + '/review-submission?round=1', {'answers': [{'itemId': goal['id'], 'numericScore': 90, 'scaleCode': None, 'opinion': '타차수 대리 제출 시도'}], 'overallOpinion': '타차수 수정'}, expected=403, label='Second-round reviewer cannot act as first-round reviewer')
    submit(manager, 1, 80)
    director.call('GET', sp + '/review-context?round=999', expected=(400, 403, 404, 422), label='Caller cannot raise round parameter to bypass previous-round visibility')
    second = director.call('GET', sp + '/review-context?round=2')
    check('Hidden previous-round policy excludes prior scores and opinions', not second['visiblePreviousRounds'])
    submit(director, 2, 90)
    start('CALCULATION')
    calculated = hr.call('POST', pp + '/calculations', {'participantIds': [pid], 'excludeIncomplete': True, 'reason': '2차 평가 완료 집계'})
    check('Actual two-reviewer plan calculates 80×40% + 90×60% = 86', calculated[0]['rawScore'] == 86)
    check_no_scores(employee.call('GET', sp + '/calculations', expected=(200, 403, 404, 409)), 'Unpublished employee cannot obtain calculation scores through direct endpoint')
    start('CALIBRATION')
    adj = director.call('PUT', sp + '/adjustment', {'adjustedScore': 90, 'adjustedGrade': 'A', 'reason': '추가 증빙 확인'})
    director.call('POST', sp + '/adjustment:complete', {'adjustedScore': 90, 'adjustedGrade': 'A', 'reason': '추가 증빙 확인'})
    start('FEEDBACK')
    fb = director.call('PUT', sp + '/feedback', {'comment': '성과가 우수하며 다음 분기 협업을 강화해 주세요.'}); fp = BASE + '/feedback/' + fb['id']
    director.call('POST', sp + '/feedback:deliver', {'comment': '성과가 우수하며 다음 분기 협업을 강화해 주세요.'})
    employee.call('POST', fp + ':appeal', {'reason': '최종 실적 증빙을 추가 확인해 주세요.'})
    director.call('POST', fp + ':resolve', {'resolution': 'SCORE_ADJUSTED', 'adjustedScore': 94, 'adjustedGrade': 'A', 'comment': '증빙에 따라 점수를 정정합니다.'})
    check('Appeal correction creates a new calculation revision', len(hr.call('GET', sp + '/calculations')) >= 2)
    hr.call('PATCH', BASE + '/participants/' + participants[1]['id'], {'status': 'EXCLUDED', 'reason': '미참여 직원 검증 종료'})
    final = hr.call('POST', pp + ':finalize', {'reason': '평가 완료 및 이의 처리 확인'})
    check('Finalization closes program without implicit individual publication', final['status'] == 'FINALIZED')
    history = employee.call('GET', BASE + '/me/history')
    check('Unpublished result does not appear in personal history', not any(x['programId'] == program['id'] for x in history['history']))
    hr.call('POST', pp + '/results:publish', {'participantIds': [pid]})
    history = employee.call('GET', BASE + '/me/history')
    check('Published grade-only result contains grade but no score', any(x['programId'] == program['id'] and x['score'] is None and x['grade'] == 'A' for x in history['history']))
    check_no_scores(employee.call('GET', sp + '/calculations', expected=(200, 403, 404)), 'Grade-only member cannot bypass masking through calculation detail')
    corrections = hr.call('GET', sp + '/calculations')
    check('Operator retains corrected score evidence', any(c['adjustedScore'] == 94 for c in corrections))
    manager.call('PUT', gp + '/opinion', {'opinion': '마감 후 수정 시도'}, expected=(403, 409, 422), label='Finalization also locks agreement opinions')
    hr.call('POST', sp + '/stage:override', {'toStage': 'REVIEW', 'toStatus': 'IN_PROGRESS', 'round': 1, 'reason': '임의 되돌림'}, expected=(409, 422), label='Finalized program blocks arbitrary state rewrite')
    # Administrative analysis uses finalized source data and real spreadsheet/image exports.
    summary = hr.call('GET', pp + '/results')
    check('Final result summary contains the corrected real participant', any(row['participantId'] == pid and row['score'] == 94 for row in summary['rows']))
    hr.call('GET', pp + '/results/items')
    hr.call('GET', pp + '/results/reviewers')
    tendencies = hr.call('GET', pp + '/analytics/reviewer-tendencies')
    check('Reviewer tendency uses actual two reviewer records', len(tendencies) == 2)
    employee.call('GET', pp + '/results', expected=403, label='Member cannot fetch organization-wide result analysis')
    pivot = {'programId': program['id'], 'rowAxes': ['department'], 'columnAxes': ['grade']}
    cells = hr.call('POST', BASE + '/analytics/pivot', pivot)['cells']
    check('Pivot counts only the finalized active population', sum(c['count'] for c in cells) == 1)
    hr.call('POST', BASE + '/analytics/pivot', {**pivot, 'columnAxes': ['job']}, expected=(400, 422), label='Pivot requires an explicit grade dimension')
    import io, zipfile, xml.etree.ElementTree as ET
    for export_path, method, body in [(pp + '/results.xlsx', 'GET', None), (pp + '/participants.xlsx', 'GET', None), (pp + '/reviewers.xlsx', 'GET', None), (BASE + '/analytics/pivot.xlsx', 'POST', pivot)]:
        raw, headers = hr.call(method, export_path, body, binary=True)
        with zipfile.ZipFile(io.BytesIO(raw)) as workbook:
            check('Real OOXML export: ' + export_path, 'xl/worksheets/sheet1.xml' in workbook.namelist())
            ET.fromstring(workbook.read('xl/worksheets/sheet1.xml'))
        check('Spreadsheet download is attachment and nosniff', 'attachment' in headers.get('content-disposition', '') and headers.get('x-content-type-options') == 'nosniff')
    svg, headers = hr.call('POST', BASE + '/analytics/pivot.svg', pivot, binary=True)
    check('Pivot chart image is a valid SVG document', ET.fromstring(svg).tag.endswith('svg'))
    preview = hr.call('GET', sp + '/employee-preview')
    check('Operator preview identifies the actual participant', preview['participant']['id'] == pid)
    employee.call('GET', sp + '/employee-preview', expected=403)

    downloaded, headers = employee.call('GET', BASE + '/guides/' + guide['id'], binary=True)
    check('Assigned employee downloads the original private guide bytes', downloaded == guide_bytes)
    hr.call('GET', BASE + '/guides/' + guide['id'], expected=(403, 404), extra_headers=foreign_headers, label='Guide lookup remains tenant-bound')
    notice = {'recipientEmployeeIds': [eid], 'companyName': '검증회사', 'subjectTemplate': '{employeeName}님 평가 결과 안내', 'bodyTemplate': '{companyName}의 공개된 결과를 확인해 주세요.', 'channel': 'IN_APP'}
    rendered = hr.call('POST', pp + '/notifications:preview', notice)
    check('Notification preview substitutes the intended template fields', '{employeeName}' not in rendered[0]['subject'] and '검증회사' in rendered[0]['body'])
    queued = hr.call('POST', pp + '/notifications:queue', notice)
    check('In-app notification is durably delivered', queued['status'] == 'SENT' and queued['queued'] == 1)
    inbox = employee.call('GET', BASE + '/me/notifications')['content']
    nid = queued['notificationIds'][0]
    check('Recipient inbox contains queued notification', any(n['id'] == nid for n in inbox))
    colleague.call('POST', BASE + '/me/notifications/' + nid + ':read', {}, expected=403)
    read = employee.call('POST', BASE + '/me/notifications/' + nid + ':read', {})
    check('Read acknowledgement is recorded', read['readAt'] is not None)
    email = hr.call('POST', pp + '/notifications:queue', {**notice, 'channel': 'EMAIL'})
    check('Unconfigured SMTP does not claim email delivery', email['status'] == 'CONFIG_REQUIRED')
    dispatch = hr.call('POST', pp + '/notifications:dispatch', {})
    check('Local runtime sends no external email', dispatch['sent'] == 0)

    copied = hr.call('POST', pp + '/copy', {k: v for k, v in {**create, 'name': '복사된 평가 ' + stamp}.items() if k != 'kind'}, expected=201)
    check('Copy starts a distinct draft without copying completed participants', copied['status'] == 'DRAFT' and copied['id'] != program['id'])
    copied_path = BASE + '/' + copied['id']
    check('Copy has no participant execution records', hr.call('GET', copied_path + '/participants')['totalElements'] == 0)
    basic = {k: v for k, v in {**create, 'name': '수정된 기본 정보 ' + stamp}.items() if k != 'kind'}
    check('Draft basic information is editable', hr.call('PUT', copied_path + '/basic', basic)['name'] == basic['name'])
    hr.call('GET', copied_path + '/analytics/reviewer-tendencies', expected=(409, 422), label='Unfinalized program cannot generate reviewer tendencies')
    return {'programId': program['id'], 'participantId': pid, 'goalId': goal['id'], 'copiedProgramId': copied['id']}


try:
    fixture = run()
except Exception as error:
    pathlib.Path(args.output).write_text(json.dumps({'passed': False, 'error': str(error), 'checks': results}, ensure_ascii=False, indent=2))
    raise
else:
    pathlib.Path(args.output).write_text(json.dumps({'passed': True, 'count': len(results), 'fixtures': fixture, 'checks': results}, ensure_ascii=False, indent=2))
    print(f'Program HTTP acceptance: {len(results)} checks passed')
