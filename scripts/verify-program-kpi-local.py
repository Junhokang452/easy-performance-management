#!/usr/bin/env python3
"""S2 real HTTP acceptance tests against a dedicated synthetic loopback runtime."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import datetime as dt
import http.cookiejar
import json
from pathlib import Path
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default='http://127.0.0.1:8089')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    parsed = urllib.parse.urlsplit(args.base_url)
    if parsed.scheme != 'http' or parsed.hostname not in ('127.0.0.1', 'localhost', '::1') or parsed.port != 8089 or parsed.username:
        raise SystemExit('Synthetic loopback HTTP runtime only')
    cases = []
    report = {'synthetic': True, 'baseUrl': args.base_url, 'cases': cases, 'completed': False}

    def check(name, valid):
        cases.append({'case': name, 'passed': bool(valid)})
        if not valid:
            raise AssertionError(name)

    class Actor:
        def __init__(self, name):
            self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
            self.call('POST', '/api/auth/session/login', {'email': f'dev-{name}@performance.dev', 'password': 'dev'})
            self.me = self.call('GET', '/api/v1/evaluation-workspace/me')

        def call(self, method, path, body=None, expected=200):
            raw = json.dumps(body, ensure_ascii=False).encode('utf-8') if body is not None else None
            req = urllib.request.Request(args.base_url + path, data=raw, method=method,
                headers={'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest'})
            try:
                with self.http.open(req, timeout=45) as response:
                    status, content = response.status, response.read()
            except urllib.error.HTTPError as error:
                status, content = error.code, error.read()
            allowed = (expected,) if isinstance(expected, int) else expected
            if status not in allowed:
                raise AssertionError(f'{method} {path}: expected {allowed}, got {status}: {content[:200]!r}')
            return json.loads(content) if content else None

    def fixture(hr, employee, manager, stamp):
        base = '/api/v1/evaluation-programs'
        program = hr.call('POST', base, {'name': 'S2 KPI synthetic ' + stamp,
            'evaluationYear': 2026, 'asOfDate': '2026-01-01', 'startsOn': '2026-01-01',
            'endsOn': '2026-12-31', 'kind': 'PERFORMANCE'}, 201)
        pp = base + '/' + program['id']
        participant = hr.call('POST', pp + '/participants',
            {'employeeId': employee.me['employeeId'], 'weightPercent': 100}, 201)
        sp = base + '/participants/' + participant['id']
        hr.call('PUT', sp + '/reviewers', {'reviewers': [
            {'employeeId': manager.me['employeeId'], 'role': 'AGREEMENT_REVIEWER', 'round': 0, 'weightPercent': 0},
            {'employeeId': manager.me['employeeId'], 'role': 'CHECKER', 'round': 0, 'weightPercent': 0},
            {'employeeId': manager.me['employeeId'], 'role': 'REVIEWER', 'round': 1, 'weightPercent': 100},
            {'employeeId': manager.me['employeeId'], 'role': 'FINAL_FEEDBACK', 'round': 0, 'weightPercent': 0}]})
        hr.call('POST', pp + '/common-items:apply', {})
        hr.call('POST', pp + '/open', {})
        hr.call('POST', pp + '/stages/GOAL:start', {'participantIds': [participant['id']], 'reason': 'S2 synthetic fixture'})
        goal = employee.call('POST', sp + '/goals', {'title': 'KPI 연계 근거 시연',
            'definition': '수동 목표와 점수는 KPI 근거 저장으로 변경하지 않는다.', 'weightPercent': 50,
            'targetValue': 777, 'unit': '건', 'achievementLevels': [{'code': 'A', 'label': '목표 달성', 'thresholdValue': 777}]}, 201)
        second_goal = employee.call('POST', sp + '/goals', {'title': '다른 목표 근거 보존',
            'definition': '목표 A 갱신은 목표 B 근거에 영향을 주지 않는다.', 'weightPercent': 50,
            'targetValue': 888, 'unit': '건', 'achievementLevels': [{'code': 'A', 'label': '목표 달성', 'thresholdValue': 888}]}, 201)
        for item in (goal, second_goal):
            employee.call('POST', base + '/goals/' + item['id'] + ':request-agreement', {})
            manager.call('POST', base + '/goals/' + item['id'] + ':decide', {'approve': True, 'opinion': 'S2 합성 목표 합의'})
        goal = next(row for row in hr.call('GET', sp + '/goals') if row['id'] == goal['id'])
        cycle = hr.call('POST', '/api/v1/cycles', {'name': 'S2 source ' + stamp,
            'periodStart': '2026-01-01', 'periodEnd': '2026-12-31', 'cycleType': 'ANNUAL'}, 201)
        tree = hr.call('POST', f'/api/v1/cycles/{cycle["id"]}/kpi-trees', {'name': '합성 개인 KPI', 'level': 'INDIVIDUAL', 'bscEnabled': False}, 201)
        node = hr.call('POST', f'/api/v1/kpi-trees/{tree["id"]}/nodes',
            {'label': '처리 건수', 'weight': 0.5, 'target': 100, 'unit': '건', 'source': 'MANUAL'}, 201)
        assignment = hr.call('POST', f'/api/v1/kpi-nodes/{node["id"]}/assignments',
            {'employeeId': employee.me['employeeId'], 'weight': 1, 'targetOverride': 200}, 201)
        actual = hr.call('POST', f'/api/v1/kpi-assignments/{assignment["id"]}/actuals',
            {'asOfDate': '2026-06-30', 'actualValue': 80, 'comment': '합성 최초 실적'}, 201)
        corrected = hr.call('POST', f'/api/v1/kpi-actuals/{actual["id"]}/supersede',
            {'asOfDate': '2026-06-30', 'actualValue': 120, 'comment': '합성 정정 실적'}, 201)
        second_node = hr.call('POST', f'/api/v1/kpi-trees/{tree["id"]}/nodes',
            {'label': '두 번째 근거', 'weight': 0.5, 'target': 100, 'unit': '건', 'source': 'MANUAL'}, 201)
        second_assignment = hr.call('POST', f'/api/v1/kpi-nodes/{second_node["id"]}/assignments',
            {'employeeId': employee.me['employeeId'], 'weight': 1, 'targetOverride': 100}, 201)
        hr.call('POST', f'/api/v1/kpi-assignments/{second_assignment["id"]}/actuals',
            {'asOfDate': '2026-06-30', 'actualValue': 90}, 201)
        return {'program': program, 'participant': participant, 'goal': goal, 'cycle': cycle,
                'secondGoal': second_goal, 'secondAssignment': second_assignment,
                'tree': tree, 'node': node, 'assignment': assignment, 'actual': actual, 'corrected': corrected}

    try:
        hr, employee, manager, colleague = [Actor(name) for name in ('hr-admin', 'employee', 'manager', 'colleague')]
        f = fixture(hr, employee, manager, dt.datetime.now().strftime('%Y%m%d%H%M%S') + uuid.uuid4().hex[:6])
        root = '/api/v1/evaluation-programs/' + f['program']['id']
        participant_path = root + '/participants/' + f['participant']['id']
        goal_path = participant_path + '/goals/' + f['goal']['id']
        second_path = participant_path + '/goals/' + f['secondGoal']['id']
        request = {'cycleId': f['cycle']['id'], 'actualCutoffDate': '2026-06-30', 'kpiAssignmentId': f['assignment']['id']}
        query = urllib.parse.urlencode({'cycleId': f['cycle']['id'], 'actualCutoffDate': '2026-06-30', 'page': 0, 'size': 10})
        candidates = hr.call('GET', participant_path + '/kpi-candidates?' + query)
        candidate = next(row for row in candidates['content'] if row['kpiAssignmentId'] == f['assignment']['id'])
        check('candidate uses corrected leaf and current target override', candidate['latestActualId'] == f['corrected']['id'] and candidate['effectiveTarget'] == 200)
        legacy = hr.call('GET', '/api/v1/kpi-assignments/my?' + urllib.parse.urlencode({'cycleId': f['cycle']['id'], 'employeeId': employee.me['employeeId']}))
        check('legacy My KPI now selects corrected successor', next(row for row in legacy if row['id'] == f['assignment']['id'])['latestActualValue'] == 120)
        hr.call('POST', f'/api/v1/kpi-assignments/{f["assignment"]["id"]}/actuals', {'asOfDate': '2026-07-01', 'actualValue': 190}, 201)
        before = hr.call('POST', goal_path + '/kpi-link:preview', request)
        check('explicit cutoff excludes future actual and includes boundary leaf', before['row']['actualId'] == f['corrected']['id'] and before['actualCutoffDate'] == '2026-06-30' and before['programAsOfDate'] == '2026-01-01')
        check('server rate and recommended score preserved', before['row']['achievementRate'] == 0.6 and before['row']['autoScore'] == 60)
        check('preview has no persisted evidence', hr.call('GET', goal_path + '/kpi-links')['totalElements'] == 0)
        for actor in (employee, manager, colleague):
            actor.call('POST', goal_path + '/kpi-link:preview', request, 403)
        check('non-operators cannot preview linkage mutations', True)
        hr.call('POST', goal_path + '/kpi-link:preview', {**request, 'actualCutoffDate': '2025-12-31'}, (400, 422))
        check('cutoff outside program and cycle rejected', True)
        hr.call('GET', participant_path + '/kpi-candidates?' + query.replace('size=10', 'size=101'), expected=(400, 422))
        check('candidate page bound enforced', True)
        apply_body = {**request, 'previewHash': before['previewHash'], 'reason': '합성 KPI 근거 명시 저장'}
        hr.call('POST', goal_path + '/kpi-link:apply', {**apply_body, 'reason': ''}, (400, 422))
        check('apply requires explicit reason', True)
        with ThreadPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(hr.call, 'POST', goal_path + '/kpi-link:apply', apply_body) for _ in range(2)]
            applied, concurrent = [future.result() for future in futures]
        check('concurrent apply persists one evidence revision', applied == concurrent and applied['revision'] == 1)
        check('same request replay is idempotent', hr.call('POST', goal_path + '/kpi-link:apply', apply_body) == applied)
        check('self and assigned reviewer can read frozen evidence', employee.call('GET', goal_path + '/kpi-links')['content'][0]['evidenceId'] == applied['evidenceId'] and manager.call('GET', goal_path + '/kpi-links')['totalElements'] == 1)
        colleague.call('GET', goal_path + '/kpi-links', expected=(403, 404))
        check('unassigned colleague cannot read another employee evidence', True)
        for actor in (employee, manager, colleague):
            actor.call('POST', goal_path + '/kpi-link:apply', apply_body, 403)
        check('non-operators cannot apply linkage', True)
        goal_after = next(row for row in hr.call('GET', '/api/v1/evaluation-programs/participants/' + f['participant']['id'] + '/goals') if row['id'] == f['goal']['id'])
        check('goal contents weight target and status remain unchanged', goal_after == f['goal'])
        second_request = {**request, 'kpiAssignmentId': f['secondAssignment']['id']}
        second_preview = hr.call('POST', second_path + '/kpi-link:preview', second_request)
        second_saved = hr.call('POST', second_path + '/kpi-link:apply', {**second_request, 'previewHash': second_preview['previewHash'], 'reason': '목표 B 근거 보존'})
        stale = hr.call('POST', goal_path + '/kpi-link:preview', request)
        hr.call('PATCH', f'/api/v1/kpi-assignments/{f["assignment"]["id"]}', {'weight': 1, 'targetOverride': 400})
        hr.call('POST', goal_path + '/kpi-link:apply', {**apply_body, 'previewHash': stale['previewHash']}, 409)
        check('changed source target invalidates old preview', True)
        refreshed = hr.call('POST', goal_path + '/kpi-link:preview', request)
        latest = hr.call('POST', goal_path + '/kpi-link:apply', {**apply_body, 'previewHash': refreshed['previewHash'], 'reason': '현재 목표값 근거 갱신'})
        check('refresh creates linked immutable revision', latest['revision'] == 2 and latest['supersedesEvidenceId'] == applied['evidenceId'] and latest['evidence']['autoScore'] == 30)
        history = hr.call('GET', goal_path + '/kpi-links')
        old = next(row for row in history['content'] if row['evidenceId'] == applied['evidenceId'])
        check('prior evidence retains captured values', old['evidence']['effectiveTarget'] == 200 and old['evidence']['actualValue'] == 120 and not old['active'])
        check('refresh goal A preserves goal B active evidence', hr.call('GET', second_path + '/kpi-links')['content'][0] == second_saved)
        old_replay = hr.call('POST', goal_path + '/kpi-link:apply', apply_body)
        check('old retry returns prior evidence without reactivation', old_replay['evidenceId'] == applied['evidenceId'] and not old_replay['active'] and hr.call('GET', goal_path + '/kpi-links')['totalElements'] == 2)
        hr.call('PATCH', f'/api/v1/kpi-assignments/{f["assignment"]["id"]}', {'weight': 1, 'targetOverride': 0})
        zero = hr.call('POST', goal_path + '/kpi-link:preview', request)
        check('zero target is blocked instead of converted into score', zero['row']['status'] == 'BLOCKED' and zero['row']['autoScore'] is None)
        hr.call('POST', goal_path + '/kpi-link:apply', {**apply_body, 'previewHash': zero['previewHash']}, 422)
        hr.call('PATCH', f'/api/v1/kpi-assignments/{f["assignment"]["id"]}', {'weight': 1, 'targetOverride': 400})
        unknown_path = goal_path.replace(f['program']['id'], str(uuid.uuid4()), 1)
        hr.call('POST', unknown_path + '/kpi-link:preview', request, 404)
        check('foreign program context rejected', True)
        foreign_assignment = hr.call('POST', f'/api/v1/kpi-nodes/{f["node"]["id"]}/assignments', {'employeeId': colleague.me['employeeId']}, 201)
        hr.call('POST', goal_path + '/kpi-link:preview', {**request, 'kpiAssignmentId': foreign_assignment['id']}, (403, 404, 422))
        check('another employee assignment cannot be bound', True)
        # Move only the fresh synthetic assignment just created above to fixture tenant B.
        # This fixed dedicated DB is never resolved from caller-controlled host or SQL text.
        foreign_id = str(uuid.UUID(foreign_assignment['id']))
        changed = subprocess.run(['C:/Program Files/PostgreSQL/18/bin/psql.exe', '-h', '127.0.0.1', '-p', '55489',
            '-U', 'performance_demo', '-d', 'performance_demo', '-v', 'ON_ERROR_STOP=1', '-At', '-c',
            f"WITH moved AS (UPDATE kpi_assignment SET tenant_id='00000000-0000-0000-0000-000000000002' WHERE id='{foreign_id}' AND tenant_id='00000000-0000-0000-0000-000000000001' RETURNING id) SELECT count(*) FROM moved"],
            check=True, capture_output=True, text=True, encoding='utf-8').stdout.strip()
        check('dedicated synthetic tenant B source fixture prepared', changed == '1')
        hr.call('POST', goal_path + '/kpi-link:preview', {**request, 'kpiAssignmentId': foreign_id}, 404)
        check('actual tenant B assignment is not exposed to tenant A', hr.call('GET', goal_path + '/kpi-links')['totalElements'] == 2)
        hr.call('POST', f'/api/v1/kpi-actuals/{f["corrected"]["id"]}/supersede', {'asOfDate': '2026-07-01', 'actualValue': 150}, 201)
        missing = hr.call('POST', goal_path + '/kpi-link:preview', request)
        check('future-dated correction never resurrects superseded root', missing['row']['status'] == 'SOURCE_MISSING' and missing['row']['actualId'] is None)
        hr.call('POST', goal_path + '/kpi-link:apply', {**apply_body, 'previewHash': missing['previewHash']}, 422)
        check('missing actual cannot produce zero-score evidence', hr.call('GET', goal_path + '/kpi-links')['totalElements'] == 2)
        # Restore eligible source by append-only actual for browser preview/apply.
        hr.call('POST', f'/api/v1/kpi-assignments/{f["assignment"]["id"]}/actuals', {'asOfDate': '2026-06-30', 'actualValue': 160}, 201)
        report.update(completed=True, fixture=f, browserProgramId=f['program']['id'], participantId=f['participant']['id'],
                      goalId=f['goal']['id'], cycleId=f['cycle']['id'], kpiAssignmentId=f['assignment']['id'], actualCutoffDate='2026-06-30')
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        Path(args.output).parent.mkdir(parents=True, exist_ok=True)
        Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'passed': sum(c['passed'] for c in cases), 'total': len(cases), 'completed': report['completed']}))


if __name__ == '__main__':
    main()
