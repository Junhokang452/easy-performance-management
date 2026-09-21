#!/usr/bin/env python3
"""S4 output/security acceptance on dedicated synthetic PG55489/API8089 only.

Program/participant shells use real APIs. Final calculation snapshots and large
populations are explicitly synthetic SQL fixtures, not workflow-completion evidence.
Never reads production configuration or connects outside loopback.
"""
import argparse
import datetime as dt
import hashlib
import http.cookiejar
import io
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.request
import uuid
from pypdf import PdfReader

BASE = 'http://127.0.0.1:8089'
API = '/api/v1/evaluation-programs'
TENANT = '00000000-0000-0000-0000-000000000001'
COLUMNS = ['EMPLOYEE_NO', 'EMPLOYEE_NAME', 'DEPARTMENT', 'POSITION', 'JOB', 'SCORE', 'GRADE', 'FEEDBACK_STATUS']
DEFAULT = dict(locale='ko', orientation='LANDSCAPE', sections=['SUMMARY', 'PARTICIPANT_TABLE'], participantColumns=COLUMNS)


class Actor:
    def __init__(self, name):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.call('POST', '/api/auth/session/login', {'email': f'dev-{name}@performance.dev', 'password': 'dev'})
        self.me = self.call('GET', '/api/v1/evaluation-workspace/me')

    def raw(self, method, path, body=None, expected=200):
        request = urllib.request.Request(BASE + path, method=method,
            data=json.dumps(body, ensure_ascii=False).encode('utf-8') if body is not None else None,
            headers={'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest'})
        try:
            with self.http.open(request, timeout=60) as response:
                status, data, headers = response.status, response.read(), dict(response.headers)
        except urllib.error.HTTPError as error:
            status, data, headers = error.code, error.read(), dict(error.headers)
        allowed = (expected,) if isinstance(expected, int) else expected
        if status not in allowed:
            raise AssertionError(f'{method} {path}: expected {allowed}, got {status}: {data[:250]!r}')
        return data, {k.lower(): v for k, v in headers.items()}

    def call(self, method, path, body=None, expected=200):
        data, _ = self.raw(method, path, body, expected)
        return json.loads(data) if data else None


def sql(statement):
    # Exact dedicated host/port/database, no environment-derived connection URL.
    run = subprocess.run(['C:/Program Files/PostgreSQL/18/bin/psql.exe', '-h', '127.0.0.1', '-p', '55489',
        '-U', 'performance_demo', '-d', 'performance_demo', '-X', '-v', 'ON_ERROR_STOP=1', '-At'],
        input=statement, text=True, encoding='utf-8', capture_output=True,
        env={**os.environ, 'PGCLIENTENCODING': 'UTF8'}, timeout=30)
    if run.returncode:
        raise AssertionError('Synthetic SQL fixture failed: ' + run.stderr[:350])
    return run.stdout.strip()


def create_program(hr, stamp, suffix):
    return hr.call('POST', API, dict(name=f'S4 합성 결과 {suffix} {stamp}', evaluationYear=2026,
        asOfDate='2026-01-01', startsOn='2026-01-01', endsOn='2026-12-31', kind='PERFORMANCE'), 201)


def seed_results(hr, employee, program, count):
    program_id = str(uuid.UUID(program['id']))
    first = hr.call('POST', API + '/' + program_id + '/participants',
        {'employeeId': employee.me['employeeId'], 'weightPercent': 100}, 201)
    participant_id = str(uuid.UUID(first['id']))
    sql(f"""
    UPDATE program_participant SET attributes_json=attributes_json ||
        '{{"name":"김하늘","employeeNo":"S4-001","orgUnitName":"제품개발부","positionCode":"책임","jobCode":"성과관리"}}'::jsonb,
        result_published=true WHERE id='{participant_id}' AND tenant_id='{TENANT}' AND program_id='{program_id}';
    INSERT INTO program_participant(id,tenant_id,program_id,employee_id,assignment_key,attributes_json,status,
        weight_percent,stage_status,current_round,result_published)
    SELECT gen_random_uuid(),tenant_id,program_id,employee_id,'s4-output-'||n,
        attributes_json || jsonb_build_object('name','검증사원'||lpad(n::text,3,'0'),
          'employeeNo','S4-'||lpad(n::text,3,'0'), 'orgUnitName',
          CASE WHEN n%10=0 THEN repeat('긴부서명검증',12) ELSE '제품개발부' END,
          'jobCode',CASE WHEN n%10=0 THEN repeat('LongUnbrokenJobCode',8) ELSE '성과관리' END),
        'ACTIVE',100,'COMPLETED',0,true
    FROM program_participant CROSS JOIN generate_series(2,{int(count)}) n WHERE id='{participant_id}' AND tenant_id='{TENANT}';
    INSERT INTO program_calculation(id,tenant_id,program_id,participant_id,revision,contributions_json,
        raw_score,normalized_score,adjusted_score,calculated_grade,status,formula,warnings_json,calculated_at)
    SELECT gen_random_uuid(),tenant_id,program_id,id,1,'[]',94.25,94.25,94.25,'A','FINAL',
        'S4 synthetic output fixture; not a workflow acceptance','[]',now()
    FROM program_participant WHERE program_id='{program_id}' AND tenant_id='{TENANT}';
    UPDATE evaluation_program SET status='FINALIZED',finalized_at=now()
        WHERE id='{program_id}' AND tenant_id='{TENANT}';
    """)
    return participant_id


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', default='_workspace/followups-20260908/s4/pdf-local.json')
    parser.add_argument('--pdf-dir', default='output/pdf/s4-synthetic')
    args = parser.parse_args()
    checks, documents = [], []
    report = {'completed': False, 'checks': checks, 'documents': documents,
              'fixtureMethod': 'API shells plus explicitly synthetic local final snapshots; not workflow verification'}
    pdf_dir = Path(args.pdf_dir); pdf_dir.mkdir(parents=True, exist_ok=True)

    def check(name, condition=True):
        checks.append({'case': name, 'passed': bool(condition)})
        if not condition:
            raise AssertionError(name)

    try:
        check('dedicated database identity', sql('SELECT current_database()') == 'performance_demo')
        hr, employee, manager = Actor('hr-admin'), Actor('employee'), Actor('manager')
        stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S') + uuid.uuid4().hex[:4]
        small = create_program(hr, stamp, '권한'); small_id = str(uuid.UUID(small['id']))
        small_path = API + '/' + small_id
        hr.raw('POST', small_path + '/results.pdf', DEFAULT, 409)
        check('non-finalized rejected before PDF body')
        small_pid = seed_results(hr, employee, small, 3)
        small_pid = str(uuid.UUID(small_pid))
        sql(f"UPDATE program_participant SET result_published=false, attributes_json=attributes_json || '{{\"name\":\"UNPUBLISHED_SECRET\"}}'::jsonb WHERE program_id='{small_id}' AND assignment_key='s4-output-2'; UPDATE program_participant SET status='EXCLUDED',attributes_json=attributes_json || '{{\"name\":\"EXCLUDED_SECRET\"}}'::jsonb WHERE program_id='{small_id}' AND assignment_key='s4-output-3';")
        summary = hr.call('GET', small_path + '/results')
        check('source uses existing active published result DTO', len(summary['rows']) == 1 and summary['rows'][0]['score'] == 94.25)
        for actor, role in [(employee, 'employee'), (manager, 'manager')]:
            actor.raw('POST', small_path + '/results.pdf', DEFAULT, 403)
            check(role + ' cannot download operator PDF')
        try:
            urllib.request.urlopen(urllib.request.Request(BASE + small_path + '/results.pdf',
                data=json.dumps(DEFAULT).encode(), headers={'Content-Type': 'application/json'}, method='POST'), timeout=30)
            raise AssertionError('Anonymous PDF request unexpectedly succeeded')
        except urllib.error.HTTPError as error:
            check('anonymous PDF request rejected', error.code == 401)
        foreign = create_program(hr, stamp, '다른테넌트'); foreign_id = str(uuid.UUID(foreign['id']))
        sql(f"UPDATE evaluation_program SET tenant_id='00000000-0000-0000-0000-000000000002' WHERE id='{foreign_id}' AND tenant_id='{TENANT}';")
        hr.raw('POST', API + '/' + foreign_id + '/results.pdf', DEFAULT, 404)
        check('tenant A cannot download known tenant B program')
        hr.raw('POST', API + '/' + str(uuid.uuid4()) + '/results.pdf', DEFAULT, 404)
        check('unknown ID same 404 as foreign program')

        invalid = [
            {'sections': []}, {'sections': ['SUMMARY', 'SUMMARY'], 'participantColumns': []},
            {'participantColumns': ['SCORE']}, {'participantColumns': ['EMPLOYEE_NAME']},
            {'orientation': 'PORTRAIT'}, {'sections': ['SUMMARY']}, {'title': 'x' * 101},
            {'title': 'bad\nheader'}, {'title': '😀'}, {'title': '  '},
            {'participantColumns': ['EMPLOYEE_NAME', 'GRADE', 'GRADE']},
            {'locale': None}, {'sections': [None]}, {'participantColumns': None},
        ]
        for index, change in enumerate(invalid):
            hr.raw('POST', small_path + '/results.pdf', {**DEFAULT, **change}, 422)
            check('invalid option rejected ' + str(index))
        for change in [{'locale': 'xx'}, {'orientation': 'A0'}, {'sections': ['HTML']}]:
            hr.raw('POST', small_path + '/results.pdf', {**DEFAULT, **change}, 400)
            check('unknown enum rejected ' + str(change))

        def export(program_id, options, name):
            data, headers = hr.raw('POST', API + '/' + program_id + '/results.pdf', options)
            check(name + ' PDF transport headers', data.startswith(b'%PDF-') and headers['content-type'].startswith('application/pdf') and
                  headers.get('content-length') == str(len(data)) and headers.get('x-content-type-options') == 'nosniff' and
                  'no-store' in headers.get('cache-control', '') and
                  f'evaluation-results-{program_id}.pdf' in headers.get('content-disposition', '') and len(data) <= 8 * 1024 * 1024)
            reader = PdfReader(io.BytesIO(data))
            text = '\n'.join(page.extract_text() for page in reader.pages)
            check(name + ' no scripts embedded files or annotations', not reader.trailer['/Root'].get('/OpenAction') and
                not reader.trailer['/Root'].get('/Names') and all(not page.get('/Annots') for page in reader.pages))
            path = pdf_dir / name; path.write_bytes(data)
            documents.append({'path': str(path.resolve()), 'pages': len(reader.pages), 'bytes': len(data),
                              'sha256': hashlib.sha256(data).hexdigest()})
            return text, reader

        text, _ = export(small_id, {**DEFAULT, 'title': '성과평가 결과 보고서'}, 'ko-landscape.pdf')
        check('Korean and exact source score survive extraction', '김하늘' in text and '94.25' in text and '성과평가 결과 보고서' in text)
        check('unpublished excluded and sensitive source never exported', all(x not in text for x in ['UNPUBLISHED_SECRET', 'EXCLUDED_SECRET', 'S4 synthetic output fixture']))
        text, _ = export(small_id, {**DEFAULT, 'orientation': 'PORTRAIT', 'sections': ['PARTICIPANT_TABLE'],
            'participantColumns': ['EMPLOYEE_NAME', 'GRADE'], 'title': '등급만 출력'}, 'ko-portrait-grade-only.pdf')
        check('column customization removes score and employee number', '94.25' not in text and 'S4-001' not in text and '김하늘' in text)
        text, _ = export(small_id, {**DEFAULT, 'locale': 'en', 'sections': ['SUMMARY'], 'participantColumns': []}, 'en-summary.pdf')
        check('summary only excludes personal columns', '김하늘' not in text and '94.25' not in text)

        large = create_program(hr, stamp, '페이지나눔'); large_id = str(uuid.UUID(large['id']))
        seed_results(hr, employee, large, 200)
        text, reader = export(large_id, {**DEFAULT, 'title': '한국어 긴 제목과 페이지 나눔 검증 ' * 4}, 'ko-multipage-200.pdf')
        check('200 rows paginate and final row retained', 1 < len(reader.pages) <= 40 and '검증사원200' in text and '검증사원002' in text)
        check('long field truncation disclosed', '...' in text)
        sql(f"INSERT INTO program_participant(id,tenant_id,program_id,employee_id,assignment_key,attributes_json,status,weight_percent,stage_status,current_round,result_published) SELECT gen_random_uuid(),tenant_id,program_id,employee_id,'s4-overlimit',attributes_json,'EXCLUDED',100,'COMPLETED',0,false FROM program_participant WHERE program_id='{large_id}' AND tenant_id='{TENANT}' LIMIT 1;")
        hr.raw('POST', API + '/' + large_id + '/results.pdf', DEFAULT, 422)
        check('201 total rows rejected even if extra row excluded')
        # Keep browser fixture at the successful 200-row bound without deleting evidence.
        browser_program = small
        audit = hr.call('GET', small_path + '/audit-events?eventType=RESULT_PDF_EXPORTED')
        check('successful downloads have exactly three small-program audit events', audit['totalElements'] == 3)
        audit_details = sql(f"SELECT details_json::text FROM program_audit_event WHERE program_id='{small_id}' AND event_type='RESULT_PDF_EXPORTED'")
        check('audit contains no plain title names or scores', all(x not in audit_details for x in ['김하늘', '성과평가 결과 보고서', '94.25', 'UNPUBLISHED_SECRET']))
        check('PDF generation did not change source results', summary == hr.call('GET', small_path + '/results'))
        empty = create_program(hr, stamp, '공개없음'); empty_id = str(uuid.UUID(empty['id']))
        sql(f"UPDATE evaluation_program SET status='FINALIZED',finalized_at=now() WHERE id='{empty_id}' AND tenant_id='{TENANT}';")
        hr.raw('POST', API + '/' + empty_id + '/results.pdf', DEFAULT, 409)
        check('no published rows fails rather than issuing empty PDF')
        xlsx, _ = hr.raw('GET', small_path + '/results.xlsx')
        check('existing XLSX unchanged and available', xlsx.startswith(b'PK'))
        report.update(completed=True, browserFixture=browser_program, largeProgramId=large_id)
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        out = Path(args.output); out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'completed': report['completed'], 'passed': sum(c['passed'] for c in checks), 'total': len(checks)}))


if __name__ == '__main__':
    main()
