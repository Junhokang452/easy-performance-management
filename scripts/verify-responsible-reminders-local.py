#!/usr/bin/env python3
"""S3 acceptance helpers for a dedicated synthetic loopback runtime only."""
import datetime as dt
import http.cookiejar
import json
import urllib.error
import urllib.request
import uuid
import argparse
import subprocess
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

BASE_URL = 'http://127.0.0.1:8089'
PROGRAMS = '/api/v1/evaluation-programs'


class Actor:
    def __init__(self, name):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.call('POST', '/api/auth/session/login', {'email': f'dev-{name}@performance.dev', 'password': 'dev'})
        self.me = self.call('GET', '/api/v1/evaluation-workspace/me')

    def call(self, method, path, body=None, expected=200):
        if not path.startswith('/api/'):
            raise ValueError('Only a local API-relative path is supported')
        raw = json.dumps(body, ensure_ascii=False).encode('utf-8') if body is not None else None
        request = urllib.request.Request(BASE_URL + path, data=raw, method=method,
            headers={'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest'})
        try:
            with self.http.open(request, timeout=45) as response:
                status, content = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, content = error.code, error.read()
        allowed = (expected,) if isinstance(expected, int) else expected
        if status not in allowed:
            raise AssertionError(f'{method} {path}: expected {allowed}, got {status}: {content[:300]!r}')
        return json.loads(content) if content else None


def create_fixture(actors):
    hr, manager = actors['hr-admin'], actors['manager']
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S') + uuid.uuid4().hex[:6]
    program = hr.call('POST', PROGRAMS, {'name': 'S3 reminder synthetic ' + stamp,
        'evaluationYear': 2026, 'asOfDate': '2026-01-01', 'startsOn': '2026-01-01',
        'endsOn': '2026-12-31', 'kind': 'PERFORMANCE'}, 201)
    root = PROGRAMS + '/' + program['id']
    people = {}
    for name in ('employee', 'colleague', 'director'):
        participant = hr.call('POST', root + '/participants',
            {'employeeId': actors[name].me['employeeId'], 'weightPercent': 100}, 201)
        people[name] = participant
        hr.call('PUT', PROGRAMS + '/participants/' + participant['id'] + '/reviewers', {'reviewers': [
            {'employeeId': manager.me['employeeId'], 'role': role, 'round': 1 if role == 'REVIEWER' else 0,
             'weightPercent': 100 if role == 'REVIEWER' else 0}
            for role in ('AGREEMENT_REVIEWER', 'CHECKER', 'REVIEWER', 'ADJUSTER', 'FINAL_FEEDBACK')]})
    hr.call('POST', root + '/common-items:apply', {})
    hr.call('POST', root + '/open', {})
    hr.call('POST', root + '/stages/GOAL:start',
        {'participantIds': [p['id'] for p in people.values()], 'reason': 'S3 synthetic fixture'})
    goals = {}
    for name, participant in people.items():
        goals[name] = actors[name].call('POST', PROGRAMS + '/participants/' + participant['id'] + '/goals',
            {'title': 'S3 합성 목표 ' + name, 'definition': '독려는 목표나 평가 점수를 변경하지 않는다.',
             'weightPercent': 100, 'targetValue': 10, 'unit': '건',
             'achievementLevels': [{'code': 'A', 'label': '목표 달성', 'thresholdValue': 10}]}, 201)
    actors['colleague'].call('POST', PROGRAMS + '/goals/' + goals['colleague']['id'] + ':request-agreement', {})
    # Keep two distinct GOAL actions and one current-round REVIEW task.
    override(hr, people['director']['id'], 'REVIEW', 1)
    return {'program': program, 'participants': people, 'goals': goals, 'stamp': stamp}


def override(hr, participant_id, stage, round_number=0, status='IN_PROGRESS'):
    return hr.call('POST', PROGRAMS + '/participants/' + participant_id + '/stage:override',
        {'toStage': stage, 'toStatus': status, 'round': round_number, 'reason': 'S3 synthetic stage fixture'})


def complete_review(actor, participant_id, round_number):
    base = PROGRAMS + '/participants/' + participant_id
    context = actor.call('GET', base + '/review-context?round=' + str(round_number))
    scales = {scale['id']: scale for scale in context['inputScales']}
    answers = []
    for item in context['items']:
        scale = scales[item['scaleId']]
        answer = {'itemId': item['id'], 'opinion': 'S3 synthetic completion'}
        if scale['kind'] == 'GRADE':
            answer['scaleCode'] = scale['levels'][0]['code']
        else:
            answer['numericScore'] = 75
        answers.append(answer)
    return actor.call('POST', base + '/review-submission:complete?round=' + str(round_number),
        {'answers': answers, 'overallOpinion': 'Synthetic acceptance only'})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    cases = []
    report = {'completed': False, 'baseUrl': BASE_URL, 'cases': cases}

    def check(name, condition):
        cases.append({'name': name, 'passed': bool(condition)})
        if not condition:
            raise AssertionError(name)

    try:
        actors = {name: Actor(name) for name in ('hr-admin', 'employee', 'manager', 'colleague', 'director')}
        hr, employee, manager = (actors[name] for name in ('hr-admin', 'employee', 'manager'))
        f = create_fixture(actors)
        root = PROGRAMS + '/' + f['program']['id']
        endpoint = root + '/incomplete-reminders'
        ids = [p['id'] for p in f['participants'].values()]
        scope = {'participantIds': ids, 'stages': [], 'locale': 'ko'}
        peek = lambda request=scope: hr.call('POST', endpoint + ':preview', request)

        def payload(preview, request=scope, keys=None):
            return {**request, 'reminderOn': preview['reminderOn'], 'previewHash': preview['previewHash'],
                    'candidateKeys': keys or [c['candidateKey'] for c in preview['candidates'] if c['status'] == 'READY'],
                    'idempotencyKey': str(uuid.uuid4()), 'reason': 'S3 local synthetic acceptance'}

        before = hr.call('GET', root + '/participants?size=100')
        audit_before = hr.call('GET', root + '/audit-events?size=100')['totalElements']
        preview = peek()
        by_action = {row['action']: row for row in preview['candidates']}
        check('draft goal routes to employee, requested goal and current review route to assignee',
              set(by_action) == {'GOAL_AUTHOR', 'GOAL_APPROVAL', 'REVIEW'} and
              by_action['GOAL_AUTHOR']['recipientEmployeeId'] == employee.me['employeeId'] and
              all(by_action[action]['recipientEmployeeId'] == manager.me['employeeId'] for action in ('GOAL_APPROVAL', 'REVIEW')))
        check('preview is read-only and deterministic', peek() == preview and hr.call('GET', endpoint)['totalElements'] == 0 and
              hr.call('GET', root + '/audit-events?size=100')['totalElements'] == audit_before)
        check('UTC date, explicit absent due date and no source opinion in rendered text', preview['zoneId'] == 'UTC' and
              preview['reminderOn'] == dt.datetime.now(dt.timezone.utc).date().isoformat() and
              all(c['dueDate'] is None and c['dueState'] == 'NO_DUE_DATE' and '독려는 목표나 평가 점수' not in c['body'] for c in preview['candidates']))
        for name in ('employee', 'manager'):
            actors[name].call('POST', endpoint + ':preview', scope, 403)
            actors[name].call('POST', endpoint + ':queue', payload(preview), 403)
            actors[name].call('GET', endpoint, expected=403)
        check('non-operator preview queue and history forbidden', True)
        for invalid in ({**scope, 'participantIds': []}, {**scope, 'participantIds': ids + [ids[0]]},
                        {**scope, 'stages': ['CALCULATION']}, {**scope, 'locale': 'fr'}):
            hr.call('POST', endpoint + ':preview', invalid, 422)
        check('empty duplicate unsupported stage and locale inputs rejected', True)
        hr.call('POST', endpoint + ':preview', {**scope, 'participantIds': [str(uuid.uuid4())]}, 404)
        check('unknown participant rejected without partial result', True)
        foreign = hr.call('POST', PROGRAMS, {'name': 'S3 tenant B ' + f['stamp'], 'evaluationYear': 2026,
            'asOfDate': '2026-01-01', 'startsOn': '2026-01-01', 'endsOn': '2026-12-31', 'kind': 'PERFORMANCE'}, 201)
        foreign_id = str(uuid.UUID(foreign['id']))
        hr.call('POST', PROGRAMS + '/' + foreign_id + '/incomplete-reminders:preview', scope, (404, 409))
        # Only this freshly created, otherwise unused synthetic program is moved in the fixed local database.
        moved = subprocess.run(['C:/Program Files/PostgreSQL/18/bin/psql.exe', '-h', '127.0.0.1', '-p', '55489',
            '-U', 'performance_demo', '-d', 'performance_demo', '-v', 'ON_ERROR_STOP=1', '-At', '-c',
            f"WITH moved AS (UPDATE evaluation_program SET tenant_id='00000000-0000-0000-0000-000000000002' WHERE id='{foreign_id}' AND tenant_id='00000000-0000-0000-0000-000000000001' RETURNING id) SELECT count(*) FROM moved"],
            check=True, capture_output=True, text=True, encoding='utf-8').stdout.strip()
        check('dedicated synthetic tenant B fixture created', moved == '1')
        for suffix, method, body in [(':preview', 'POST', scope), (':queue', 'POST', payload(preview)), ('', 'GET', None)]:
            hr.call(method, PROGRAMS + '/' + foreign_id + '/incomplete-reminders' + suffix, body, 404)
        check('actual tenant B program cannot be previewed queued or read by tenant A', True)
        hr.call('POST', endpoint + ':queue', {**payload(preview), 'reason': '   '}, 422)
        check('blank reason never queues', hr.call('GET', endpoint)['totalElements'] == 0)

        command = payload(preview)
        hr2 = Actor('hr-admin')
        with ThreadPoolExecutor(max_workers=2) as pool:
            concurrent = list(pool.map(lambda actor: actor.call('POST', endpoint + ':queue', command), (hr, hr2)))
        check('concurrent same key exact replay creates three notifications once', concurrent[0] == concurrent[1] and
              concurrent[0]['queued'] == 3 and hr.call('GET', endpoint)['totalElements'] == 3)
        check('queue does not mutate participant stages', hr.call('GET', root + '/participants?size=100') == before)
        duplicate = hr.call('POST', endpoint + ':queue', {**command, 'idempotencyKey': str(uuid.uuid4())})
        check('new idempotency key same source reuses daily rows', duplicate['queued'] == 0 and duplicate['duplicateSuppressed'] == 3 and
              {r['notificationId'] for r in duplicate['rows']} == {r['notificationId'] for r in concurrent[0]['rows']})
        check('dedupe status does not change source preview hash', peek()['previewHash'] == preview['previewHash'] and
              all(c['status'] == 'ALREADY_QUEUED' for c in peek()['candidates']))
        hr.call('POST', endpoint + ':queue', {**command, 'reason': 'changed request'}, 409)
        check('same key different request conflicts', True)
        own_row = next(r for r in concurrent[0]['rows'] if r['recipientEmployeeId'] == employee.me['employeeId'])
        inbox = employee.call('GET', PROGRAMS + '/me/notifications?size=100')['content']
        check('inbox contains only recipient notifications and IN_APP SENT', any(r['id'] == own_row['notificationId'] for r in inbox) and
              all(r['recipientEmployeeId'] == employee.me['employeeId'] for r in inbox) and
              all(r['notificationStatus'] == 'SENT' for r in concurrent[0]['rows']) and
              next(r for r in inbox if r['id'] == own_row['notificationId'])['channel'] == 'IN_APP')
        manager.call('POST', PROGRAMS + '/me/notifications/' + own_row['notificationId'] + ':read', {}, 403)
        employee.call('POST', PROGRAMS + '/me/notifications/' + own_row['notificationId'] + ':read', {})
        check('read is recipient-bound and replay remains immutable', hr.call('POST', endpoint + ':queue', command) == concurrent[0] and
              any(r['notificationId'] == own_row['notificationId'] and r['readAt'] is not None for r in hr.call('GET', endpoint)['content']))

        participant_id = f['participants']['employee']['id']
        stale = peek()
        employee.call('POST', PROGRAMS + '/goals/' + f['goals']['employee']['id'] + ':request-agreement', {})
        hr.call('POST', endpoint + ':queue', {**payload(stale, keys=[c['candidateKey'] for c in stale['candidates']]), 'idempotencyKey': str(uuid.uuid4())}, 409)
        check('real source and owner change rejects old preview atomically', hr.call('GET', endpoint)['totalElements'] == 3)
        fresh = peek()
        changed = next(c for c in fresh['candidates'] if c['participantId'] == participant_id)
        check('new action episode changes owner without recipient fallback', changed['action'] == 'GOAL_APPROVAL' and changed['status'] == 'READY')
        with ThreadPoolExecutor(max_workers=2) as pool:
            commands = [payload(fresh, keys=[changed['candidateKey']]) for _ in range(2)]
            results = list(pool.map(lambda pair: pair[0].call('POST', endpoint + ':queue', pair[1]), zip((hr, hr2), commands)))
        check('concurrent different keys dedupe under shared program lock', sum(r['queued'] for r in results) == 1 and
              sum(r['duplicateSuppressed'] for r in results) == 1 and hr.call('GET', endpoint)['totalElements'] == 4)

        single_scope = {**scope, 'participantIds': [participant_id]}
        for stage, expected_action, owner in [('INTERMEDIATE', 'INTERMEDIATE_CHECK', manager), ('SELF_REVIEW', 'SELF_REVIEW', employee), ('FEEDBACK', 'FEEDBACK_DELIVERY', manager)]:
            override(hr, participant_id, stage)
            row = peek(single_scope)['candidates'][0]
            check(stage + ' derives exact responsible owner', row['action'] == expected_action and row['recipientEmployeeId'] == owner.me['employeeId'] and row['status'] == 'READY')
        feedback = manager.call('POST', PROGRAMS + '/participants/' + participant_id + '/feedback:deliver', {'comment': 'S3 secret feedback must not enter reminders'})
        row = peek(single_scope)['candidates'][0]
        check('delivered feedback routes to employee without leaking comment', row['action'] == 'FEEDBACK_ACKNOWLEDGEMENT' and
              row['recipientEmployeeId'] == employee.me['employeeId'] and 'S3 secret feedback' not in row['body'])
        employee.call('POST', PROGRAMS + '/feedback/' + feedback['id'] + ':appeal', {'reason': 'S3 synthetic appeal'})
        row = peek(single_scope)['candidates'][0]
        check('appealed feedback routes to actual FINAL_FEEDBACK assignee', row['action'] == 'FEEDBACK_RESOLUTION' and row['recipientEmployeeId'] == manager.me['employeeId'])
        manager.call('POST', PROGRAMS + '/feedback/' + feedback['id'] + ':resolve', {'resolution': 'UPHELD', 'comment': 'S3 synthetic resolved'})
        check('assignee really can resolve and completed stage is excluded', not peek(single_scope)['candidates'] and peek(single_scope)['exclusions'][0]['status'] == 'COMPLETED')
        override(hr, participant_id, 'CALIBRATION')
        check('missing calculation is blocked instead of fabricated owner message', all(c['status'] == 'BLOCKED' for c in peek(single_scope)['candidates']) and peek(single_scope)['summary']['blocked'] > 0)
        override(hr, participant_id, 'CALCULATION')
        check('operator-only calculation has no inferred HR recipient', not peek(single_scope)['candidates'] and peek(single_scope)['exclusions'][0]['reasonCode'] == 'OWNER_UNADDRESSABLE')
        override(hr, participant_id, 'REVIEW', 1, 'NOT_STARTED')
        check('not-started work is excluded', not peek(single_scope)['candidates'] and peek(single_scope)['exclusions'][0]['reasonCode'] == 'STAGE_NOT_STARTED')
        override(hr, participant_id, 'REVIEW', 1, 'BLOCKED')
        blocked_preview = peek(single_scope)
        check('blocked stage cannot be queued', blocked_preview['summary']['blocked'] == 1 and blocked_preview['candidates'][0]['body'] is None)
        hr.call('POST', endpoint + ':queue', payload(blocked_preview, single_scope, [blocked_preview['candidates'][0]['candidateKey']]), 422)
        override(hr, participant_id, 'REVIEW', 1)
        check('filter excludes unsupported current selection', not peek({**single_scope, 'stages': ['GOAL']})['candidates'])
        for locale in ('ko', 'en', 'ja', 'zh-CN', 'vi'):
            row = peek({**single_scope, 'locale': locale})['candidates'][0]
            check(locale + ' server message template is bounded and relative', 0 < len(row['subject']) <= 200 and
                  0 < len(row['body']) <= 8000 and row['deepLink'].startswith('/admin/evaluation-programs/'))
        expired = peek(single_scope)
        hr.call('POST', endpoint + ':queue', {**payload(expired, single_scope), 'reminderOn': '2000-01-01'}, 409)
        check('wrong UTC day requires new preview', True)
        hr.call('GET', endpoint + '?size=101', expected=422)
        check('history page bound enforced', True)
        director_id = f['participants']['director']['id']
        complete_review(manager, director_id, 1)
        finished_review = peek({**scope, 'participantIds': [director_id]})
        check('actual reviewer completion excludes the old work episode', not finished_review['candidates'] and
              finished_review['exclusions'][0]['status'] == 'COMPLETED')
        hr.call('POST', root + '/notifications:queue', {'recipientEmployeeIds': [hr.me['employeeId']],
            'companyName': 'Synthetic local', 'subjectTemplate': 'S3 generic regression',
            'bodyTemplate': 'Unrelated generic IN_APP row', 'channel': 'IN_APP'})
        check('S3 history excludes existing generic notification rows', hr.call('GET', endpoint)['totalElements'] == 4)
        # Browser gets a separate fresh fixture; no API acceptance state is reset or overwritten.
        browser_fixture = create_fixture(actors)
        report.update(completed=True, fixture=f, browserFixture=browser_fixture)
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        Path(args.output).parent.mkdir(parents=True, exist_ok=True)
        Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'passed': sum(c['passed'] for c in cases), 'total': len(cases), 'completed': report['completed']}))


if __name__ == '__main__':
    main()
