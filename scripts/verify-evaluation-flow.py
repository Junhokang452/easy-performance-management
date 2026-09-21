#!/usr/bin/env python3
"""Exercise a real local evaluation with synthetic actor accounts. No external URLs allowed."""
import argparse
import base64
import hashlib
import hmac
import datetime as dt
import http.cookiejar
import json
import pathlib
import urllib.error
import urllib.parse
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:8087')
parser.add_argument('--secrets-file', default=str(pathlib.Path(__file__).resolve().parents[1]/'.local-demo/secrets.env'))
parser.add_argument('--output', default='/tmp/performance-flow-result.json')
args = parser.parse_args()
if urllib.parse.urlsplit(args.base_url).hostname not in ('localhost', '127.0.0.1', '::1'):
    raise SystemExit('Only an isolated local demo URL is permitted')
RESULTS = []
BASE = '/api/v1/evaluation-workspace'

class Actor:
    def __init__(self, name):
        self.name = name
        self.jar = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.session = self.call('POST', '/api/auth/session/login',
            {'email': f'dev-{name}@performance.dev', 'password': 'dev'})

    def call(self, method, path, payload=None, expected=200, label=None, headers=None):
        body = json.dumps(payload).encode() if payload is not None else None
        request = urllib.request.Request(args.base_url + urllib.parse.quote(path, safe='/?=&%'), data=body, method=method,
            headers={'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest', **(headers or {})})
        try:
            response = self.http.open(request, timeout=30)
            status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        try:
            data = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            data = raw.decode()[:500]
        allowed = expected if isinstance(expected, tuple) else (expected,)
        case = label or f'{self.name}: {method} {path}'
        if status not in allowed:
            RESULTS.append({'case': case, 'passed': False, 'status': status, 'body': data})
            raise AssertionError(f'{case}: expected {allowed}, got {status}: {data}')
        RESULTS.append({'case': case, 'passed': True, 'status': status})
        return data

def check(label, condition):
    RESULTS.append({'case': label, 'passed': bool(condition)})
    if not condition:
        raise AssertionError(label)

def run():
    hr, employee, manager, colleague = [Actor(name) for name in ('hr-admin','employee','manager','colleague')]
    check('Browser metadata has no authentication credentials', not {'accessToken','refreshToken'} & hr.session.keys())
    me = employee.call('GET', BASE+'/me')
    check('Employee is explicitly bound to Core Master identity', me['employeeId']=='019ed002-0000-7000-8000-000000000001')
    hr.call('GET', BASE+'/directory?q=김나리&page=0&size=10', label='HR can search by employee name')
    employee.call('POST','/api/v1/cycles',{},expected=(403,),label='Employee cannot bypass workflow using legacy administrator API')
    hr.call('POST','/api/v1/cycles',{'name':'잘못된 평가 유형','periodStart':'2026-07-01','periodEnd':'2026-12-31','cycleType':'INVALID'},expected=400,label='Invalid enum input returns a client error')
    stamp=dt.datetime.now().strftime('%H%M%S')
    policy={'distributionMode':'HYBRID','ratingScale':'S_A_B_C_D','appealEnabled':True,'bscEnabled':False,'achievementLogCutoffDays':3,'forcedDistribution':None}
    cycle=hr.call('POST','/api/v1/cycles',{'name':f'전체 과정 검증 {stamp}','periodStart':'2026-07-01','periodEnd':'2026-12-31','cycleType':'HALF_ANNUAL','policy':policy},expected=201)
    cycle_path=BASE+'/cycles/'+cycle['id']
    hr.call('POST',cycle_path+'/open',{},expected=(409,422),label='Opening without participants is blocked')
    roster=hr.call('PUT',cycle_path+'/participants',{'participants':[{'employeeId':me['employeeId'],'managerEmployeeId':'019ed002-0000-7000-8000-000000000002'}]})
    participant=roster['items'][0]
    opened=hr.call('POST',cycle_path+'/open',{})
    check('Open reaches goal setting',opened['cycle']['status']=='GOAL_SETTING')
    for actor in (employee,manager):
        discovered=actor.call('GET',BASE+'/cycles?page=0&size=100')
        check(actor.name+' discovers assigned cycle',any(item['id']==cycle['id'] for item in discovered['content']))
    other_cycles=colleague.call('GET',BASE+'/cycles?page=0&size=100')
    check('Unassigned colleague does not discover private cycle',not any(item['id']==cycle['id'] for item in other_cycles['content']))
    colleague.call('GET',cycle_path,expected=(403,404),label='Unassigned colleague cannot read private cycle metadata')
    colleague.call('GET',BASE+'/participants/'+participant['id']+'/goals',expected=(403,404),label='Unassigned colleague cannot read another employee goals')
    # A signed local fixture for tenant B proves server data scoping, rather than merely header handling.
    secrets_path=pathlib.Path(args.secrets_file)
    if secrets_path.exists():
        secrets=dict(line.split('=',1) for line in secrets_path.read_text().splitlines() if '=' in line)
        token=hr.call('POST','/api/auth/login',{'email':'dev-hr-admin@performance.dev','password':'dev'})['accessToken']
        header,payload,_=token.split('.')
        claims=json.loads(base64.urlsafe_b64decode(payload+'='*((-len(payload))%4)))
        claims.update(sub='019ed004-0000-7000-8000-000000000002',tid='00000000-0000-0000-0000-000000000002')
        payload=base64.urlsafe_b64encode(json.dumps(claims,separators=(',',':')).encode()).rstrip(b'=').decode()
        signing=(header+'.'+payload).encode()
        signature=base64.urlsafe_b64encode(hmac.new(secrets['PERFORMANCE_DEMO_JWT_SECRET'].encode(),signing,hashlib.sha512).digest()).rstrip(b'=').decode()
        foreign_headers={'Authorization':'Bearer '+header+'.'+payload+'.'+signature}
        foreign=hr.call('GET',BASE+'/me',headers=foreign_headers,label='Valid second-tenant actor is authenticated')
        check('Second-tenant context preserved',foreign['tenantId']=='00000000-0000-0000-0000-000000000002')
        hr.call('GET',BASE+'/participants/'+participant['id']+'/goals',headers=foreign_headers,expected=(403,404),label='Tenant B cannot read tenant A participants')
    else:
        raise AssertionError('Local JWT fixture secret is required for the cross-tenant test')
    def advance(phase, expected=200, label=None):
        return hr.call('POST',cycle_path+'/advance',{'targetStatus':phase},expected=expected,label=label)
    advance('MID_REVIEW',expected=(409,422),label='Cannot leave goal setting before approved goals')
    employee.call('POST',cycle_path+'/goals',{'employeeId':'019ed002-0000-7000-8000-000000000006','title':'다른 직원으로 위장','weight':1,'target':100},expected=(403,404),label='Request employee ID cannot replace authenticated actor')
    goal=employee.call('POST',cycle_path+'/goals',{'title':'고객 응답 시간 개선','description':'평균 응답 기준을 측정합니다.','weight':1,'target':100,'unit':'건'},expected=201)
    goal_path=BASE+'/goals/'+goal['id']
    restored=employee.call('GET',cycle_path+'/me')['goals'][0]
    check('Goal reload preserves agreed weight, target and unit',restored['weight']==1 and restored['target']==100 and restored['unit']=='건')
    employee.call('POST',goal_path+'/submit',{})
    employee.call('POST',goal_path+'/decision',{'decision':'APPROVE','comment':'자기 승인 시도'},expected=(403,),label='Employee cannot approve own goal')
    manager.call('POST',goal_path+'/decision',{'decision':'REJECT','comment':'측정 기준을 구체화해 주세요.'})
    restored=employee.call('GET',cycle_path+'/me')['goals'][0]
    check('Rejected goal keeps numeric form values for revision',restored['weight']==1 and restored['target']==100)
    employee.call('PATCH',goal_path,{'description':'분기 고객 문의 100건의 응답을 완료합니다.'})
    employee.call('POST',goal_path+'/submit',{})
    approved=manager.call('POST',goal_path+'/decision',{'decision':'APPROVE','comment':'수정 목표에 합의합니다.'})
    check('Rejected goal can be revised, resubmitted and approved',approved['status']=='APPROVED')
    advance('MID_REVIEW')
    employee.call('POST',goal_path+'/check-ins',{'asOfDate':dt.date.today().isoformat(),'note':'실적 값 누락'},expected=422,label='Actual numeric value is required')
    for progress in (-1, 101):
        employee.call('POST',goal_path+'/check-ins',{'asOfDate':dt.date.today().isoformat(),'actualValue':90,'progressPercent':progress},expected=422,label=f'Invalid progress {progress} is rejected')
    employee.call('POST',goal_path+'/check-ins',{'asOfDate':dt.date.today().isoformat(),'actualValue':90,'progressPercent':90,'note':'진행 실적 90건'},expected=201)
    history=employee.call('GET',goal_path+'/check-ins')
    check('Numeric actual check-in persisted',len(history)==1 and history[0]['actualValue']==90)
    check('Check-in progress survives reload',history[0]['progressPercent']==90)
    advance('SELF_REVIEW',expected=(409,422),label='Intermediate review completion gates self evaluation')
    progress={'progressSummary':'고객 문의 90건에 응답했습니다.','achievements':'처리 지연 감소','blockers':'복잡한 문의 대응','supportNeeded':'사례 교육'}
    employee.call('PUT',cycle_path+'/me/intermediate-review',progress)
    employee.call('POST',cycle_path+'/me/intermediate-review/submit',progress)
    mid_path=BASE+'/participants/'+participant['id']+'/intermediate-review'
    manager.call('PUT',mid_path,{'managerComment':'사례 교육을 지원하겠습니다.'})
    completed=manager.call('POST',mid_path+'/complete',{})
    check('Manager completes intermediate performance review',completed['status']=='MANAGER_COMPLETED')
    advance('SELF_REVIEW')
    workspace=employee.call('GET',cycle_path+'/me')
    review_id=workspace['review']['id']
    review_path=BASE+'/reviews/'+review_id
    manager.call('POST',review_path+'/self/submit',{'comment':'다른 직원 대신 제출'},expected=403,label='Manager cannot submit employee self evaluation')
    for method,path,payload in (
        ('POST','/api/v1/cycles/'+cycle['id']+'/transition',{'toStatus':'FINALIZED'}),
        ('POST','/api/v1/reviews/'+review_id+'/transition',{'targetStatus':'FINALIZED'}),
        ('POST','/api/v1/cycles/'+cycle['id']+'/reports/publish',{}),
    ):
        hr.call(method,path,payload,expected=403,label='Legacy operator mutation cannot bypass workflow: '+path)
    colleague.call('GET',review_path,expected=(403,404),label='Unassigned colleague cannot read review')
    employee.call('POST',review_path+'/self/draft',{'comment':'초안: 응답 품질을 개선했습니다.'})
    employee.call('POST',review_path+'/self/submit',{'comment':'90건 완료, 다음 분기에는 처리 시간을 단축하겠습니다.'})
    advance('MANAGER_REVIEW')
    items=manager.call('GET',review_path+'/kpi-items')
    check('Manager sees latest numeric actual',items[0]['latestActualValue']==90)
    score={'comment':'정확한 응답과 협업이 돋보였습니다.','itemScores':[{'assignmentId':item['assignmentId'],'managerScore':90} for item in items]}
    manager.call('POST',review_path+'/manager/draft',score)
    manager.call('POST',review_path+'/manager/submit',score)
    for path in (cycle_path+'/me',review_path):
        response=employee.call('GET',path)
        review=response['review'] if 'review' in response else response
        check('Unpublished manager evaluation is masked: '+path,all(review.get(k) is None for k in ('managerComment','kpiScore','finalScore','finalGrade','kpiScoreDetail')))
    advance('CALIBRATION')
    session=hr.call('POST',cycle_path+'/calibration-sessions',{})
    hr.call('POST',BASE+'/calibration-sessions/'+session['id']+'/adjustments',{'reviewId':review_id,'toGrade':'A','reason':'성과 증빙과 평가 기준을 함께 검토했습니다.'})
    confirmed=hr.call('POST',cycle_path+'/calibration/confirm',{'sessionId':session['id']})
    check('Calibration confirms actual review',confirmed['finalizedCount']==1)
    advance('FINALIZED',expected=(409,422),label='Cannot close before publication and feedback')
    hr.call('POST',cycle_path+'/reports/publish',{})
    report=employee.call('GET',cycle_path+'/report')
    check('Published result includes confirmed grade',report['content']['finalGrade']=='A')
    report_path=BASE+'/reports/'+report['id']
    colleague.call('POST',report_path+'/acknowledge',{},expected=(403,404),label='Colleague cannot acknowledge another employee result')
    manager.call('PUT',report_path+'/feedback',{'comment':'고객 사례를 공유하며 다음 목표를 정하겠습니다.'})
    manager.call('POST',report_path+'/feedback/complete',{'comment':'면담 완료: 다음 분기에는 사례 교육을 함께 진행합니다.'})
    employee.call('POST',report_path+'/appeal',{'reason':'추가 협업 성과 증빙을 검토해 주세요.'})
    advance('FINALIZED',expected=(409,422),label='Unresolved appeal prevents close')
    hr.call('POST',report_path+'/appeal/resolve',{'resolution':'ADJUSTMENT_REQUIRED','comment':'지원하지 않는 정정 경로를 완료 처리하면 안 됩니다.'},expected=422,label='Unimplemented grade correction cannot silently resolve an appeal')
    resolved=hr.call('POST',report_path+'/appeal/resolve',{'resolution':'UPHELD','comment':'추가 자료를 검토했고 기존 등급 기준에 포함된 성과임을 확인했습니다.'})
    check('HR appeal decision persisted',resolved['status']=='RESOLVED')
    employee.call('POST',report_path+'/acknowledge',{})
    closed=hr.call('POST',cycle_path+'/close',{})
    check('Entire evaluation cycle closes',closed['status']=='FINALIZED')
    employee.call('PATCH',goal_path,{'title':'종료 후 변경 시도'},expected=(409,422),label='Closed evaluation rejects goal mutation')
    manager.call('POST',report_path+'/feedback/complete',{'comment':'종료 후 변경'},expected=(409,422),label='Closed evaluation rejects feedback mutation')
    summary=hr.call('GET',cycle_path+'/results/summary')
    check('Result summary matches actual completion',summary['publishedCount']==1 and summary['acknowledgedCount']==1)
    return {'environment':'isolated local PostgreSQL; tenant-ID access guards tested; Neon physical routing not exercised',
            'verifiedAt':dt.datetime.now(dt.timezone.utc).isoformat(),
            'cycleId':cycle['id'],'participantId':participant['id'],'results':RESULTS}

if __name__=='__main__':
    outcome={}
    try:
        outcome=run()
    finally:
        outcome['results']=RESULTS
        pathlib.Path(args.output).write_text(json.dumps(outcome,ensure_ascii=False,indent=2))
    print(json.dumps({'passed':sum(r['passed'] for r in RESULTS),'failed':sum(not r['passed'] for r in RESULTS),'cycleId':outcome.get('cycleId')},ensure_ascii=False))
