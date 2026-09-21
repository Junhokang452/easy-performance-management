#!/usr/bin/env python3
"""Verify cancel/recalculate/recalibrate/refeedback using an existing local full-program fixture."""
import argparse
import http.cookiejar
import json
import pathlib
import urllib.error
import urllib.parse
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:8087')
parser.add_argument('--fixture', default='_workspace/full-cycle-20260907/program-api-result.json')
parser.add_argument('--output', default='_workspace/full-cycle-20260907/program-revision-result.json')
args = parser.parse_args()
if urllib.parse.urlsplit(args.base_url).hostname not in ('localhost', '127.0.0.1', '::1'):
    raise SystemExit('Only a synthetic local runtime is allowed')

BASE = '/api/v1/evaluation-programs'
checks = []


def check(label, condition):
    checks.append({'case': label, 'passed': bool(condition)})
    if not condition:
        raise AssertionError(label)


class Actor:
    def __init__(self, name):
        self.name = name
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.call('POST', '/api/auth/session/login', {'email': f'dev-{name}@performance.dev', 'password': 'dev'})

    def call(self, method, path, body=None, expected=200, label=None):
        headers = {'X-Requested-With': 'XMLHttpRequest'}
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        request = urllib.request.Request(args.base_url + urllib.parse.quote(path, safe='/?=&%'), data=data,
                                         method=method, headers=headers)
        try:
            with self.http.open(request, timeout=30) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        allowed = expected if isinstance(expected, tuple) else (expected,)
        case = label or f'{self.name} {method} {path}'
        checks.append({'case': case, 'passed': status in allowed, 'status': status})
        if status not in allowed:
            raise AssertionError(f'{case}: expected {allowed}, got {status}: {raw[:800]!r}')
        return json.loads(raw) if raw else None


def contains(node, text):
    if isinstance(node, dict):
        return any(contains(key, text) or contains(value, text) for key, value in node.items())
    if isinstance(node, list):
        return any(contains(value, text) for value in node)
    return text in str(node)


def run():
    fixture_document = json.loads(pathlib.Path(args.fixture).read_text())
    check('Full-program fixture completed before revision verification', fixture_document.get('passed') is True)
    fixture = fixture_document['fixtures']
    program_id, participant_id = fixture['programId'], fixture['participantId']
    pp, sp = f'{BASE}/{program_id}', f'{BASE}/participants/{participant_id}'
    hr, director, employee = Actor('hr-admin'), Actor('director'), Actor('employee')

    cancelled = hr.call('POST', pp + ':cancel-finalization', {'reason': '재집계 검증을 위한 마감 취소'})
    check('Finalization cancellation reopens the program', cancelled['status'] == 'OPEN')
    history = employee.call('GET', BASE + '/me/history')
    check('Cancellation immediately withdraws the previously published result',
          not any(row['programId'] == program_id for row in history['history']))

    hr.call('POST', sp + '/stage:override', {
        'toStage': 'CALCULATION', 'toStatus': 'IN_PROGRESS', 'round': 0, 'reason': '마감 취소 후 재계산'})
    recalculated = hr.call('POST', pp + '/calculations', {
        'participantIds': [participant_id], 'excludeIncomplete': False, 'reason': '마감 취소 후 새 집계 revision'})
    check('Recalculation appends a new final calculation revision',
          len(recalculated) == 1 and recalculated[0]['revision'] >= 3 and recalculated[0]['adjustedScore'] == 86)

    # Re-complete every other finalization prerequisite first, leaving only the old calculation-linked adjustment stale.
    hr.call('POST', sp + '/stage:override', {
        'toStage': 'FEEDBACK', 'toStatus': 'IN_PROGRESS', 'round': 0, 'reason': '재계산 결과 피드백 재작성'})
    feedback = director.call('PUT', sp + '/feedback', {'comment': '재집계 결과를 다시 안내합니다.'})
    director.call('POST', sp + '/feedback:deliver', {'comment': '재집계 결과를 다시 안내합니다.'})
    agreed = employee.call('POST', BASE + '/feedback/' + feedback['id'] + ':agree', {})
    check('Fresh feedback is complete before isolating the stale-adjustment blocker', agreed['status'] == 'AGREED')
    blocked = hr.call('POST', pp + ':finalize', {'reason': '오래된 보정으로 마감 시도'}, expected=(409, 422),
                      label='Stale adjustment cannot finalize a recalculated result')
    check('Finalization returns the incomplete-workflow contract when stale adjustment is the only blocker',
          contains(blocked, 'E9804938'))

    hr.call('POST', sp + '/stage:override', {
        'toStage': 'CALIBRATION', 'toStatus': 'IN_PROGRESS', 'round': 0, 'reason': '새 계산 revision 보정'})
    adjusted = director.call('POST', sp + '/adjustment:complete', {
        'adjustedScore': 95, 'adjustedGrade': 'S', 'reason': '새 계산 revision 근거로 재보정'})
    check('New calibration revision is linked to the new calculation',
          adjusted['calculationId'] == recalculated[0]['id'] and adjusted['revision'] >= 3)

    hr.call('POST', sp + '/stage:override', {
        'toStage': 'FEEDBACK', 'toStatus': 'COMPLETED', 'round': 0, 'reason': '재보정 후 완료된 피드백 단계 복귀'})

    finalized = hr.call('POST', pp + ':finalize', {'reason': '재계산·재보정·피드백 완료'})
    check('Program can finalize only after fresh linked outcomes', finalized['status'] == 'FINALIZED')
    published = hr.call('POST', pp + '/results:publish', {'participantIds': [participant_id]})
    check('Republishing changes exactly the intended participant',
          published['changedCount'] == 1 and participant_id in published['changedParticipantIds'])
    summary = hr.call('GET', pp + '/results')
    check('Published result uses the new adjustment rather than the stale one',
          any(row['participantId'] == participant_id and row['score'] == 95 and row['grade'] == 'S'
              for row in summary['rows']))
    member_history = employee.call('GET', BASE + '/me/history')
    check('Grade-only member history republishes only the new effective grade',
          any(row['programId'] == program_id and row['score'] is None and row['grade'] == 'S'
              for row in member_history['history']))
    return {'programId': program_id, 'participantId': participant_id,
            'calculationRevision': recalculated[0]['revision'], 'adjustmentRevision': adjusted['revision']}


try:
    final_fixture = run()
except Exception as error:
    pathlib.Path(args.output).write_text(json.dumps({'passed': False, 'error': str(error), 'checks': checks}, ensure_ascii=False, indent=2))
    raise
else:
    pathlib.Path(args.output).write_text(json.dumps({'passed': True, 'fixtures': final_fixture, 'count': len(checks),
                                                     'checks': checks}, ensure_ascii=False, indent=2))
    print(f'Program revision HTTP acceptance: {len(checks)} checks passed')
