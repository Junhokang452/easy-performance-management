#!/usr/bin/env python3
"""Independent numeric policy checks against the isolated synthetic local demo."""
import argparse
import datetime as dt
import http.cookiejar
import json
import pathlib
import urllib.error
import urllib.parse
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:8087')
parser.add_argument('--output', default='_workspace/full-cycle-20260907/program-calculation-policy-result.json')
args = parser.parse_args()
if urllib.parse.urlsplit(args.base_url).hostname not in ('localhost', '127.0.0.1', '::1'):
    raise SystemExit('Only a synthetic local runtime is allowed')

BASE = '/api/v1/evaluation-programs'
RESOURCE = '/api/v1/evaluation-resources'
results = []


def check(label, condition, evidence=None):
    row = {'case': label, 'passed': bool(condition)}
    if evidence is not None:
        row['evidence'] = evidence
    results.append(row)
    if not condition:
        raise AssertionError(f'{label}: {evidence!r}')


class Actor:
    def __init__(self, name):
        self.name = name
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.call('POST', '/api/auth/session/login', {'email': f'dev-{name}@performance.dev', 'password': 'dev'})
        self.me = self.call('GET', '/api/v1/evaluation-workspace/me')

    def call(self, method, path, body=None, expected=200, label=None):
        headers = {'X-Requested-With': 'XMLHttpRequest'}
        if body is not None:
            body = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        request = urllib.request.Request(args.base_url + urllib.parse.quote(path, safe='/?=&%,:'), data=body,
                                         method=method, headers=headers)
        try:
            with self.http.open(request, timeout=30) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        allowed = expected if isinstance(expected, tuple) else (expected,)
        label = label or f'{self.name} {method} {path}'
        results.append({'case': label, 'passed': status in allowed, 'status': status})
        if status not in allowed:
            raise AssertionError(f'{label}: expected {allowed}, got {status}: {raw[:500]!r}')
        return json.loads(raw) if raw else None


def request(name):
    return {'name': name, 'evaluationYear': 2026, 'asOfDate': '2026-01-01',
            'startsOn': '2026-01-01', 'endsOn': '2026-12-31', 'kind': 'PERFORMANCE'}


def create(hr, name):
    program = hr.call('POST', BASE, request(name), expected=(200, 201))
    return program, BASE + '/' + program['id']


def calculation_only(config):
    for stage in config['stages']:
        stage['enabled'] = stage['stage'] in ('REVIEW', 'CALCULATION')
    group = config['groups'][0]
    group['intermediateEnabled'] = False
    group['selfReviewEnabled'] = False
    group['itemAssignmentMode'] = 'DESIGNATED'
    config['publication']['feedbackEnabled'] = False
    config['publication']['appealEnabled'] = False
    return group


def set_one_item_per_round(config, group_id, scale_id, rounds):
    config['commonItems'] = [
        {'id': str(uuid.uuid4()), 'groupId': group_id, 'round': round_number, 'catalogItemId': None,
         'title': f'독립 계산 항목 {round_number}', 'definition': '환산과 가중치 검증', 'weightPercent': 100,
         'scaleId': scale_id, 'displayOrder': 0, 'opinionRequired': False}
        for round_number in rounds
    ]


def configure(hr, path, config, reason):
    return hr.call('PUT', path + '/definition', {'configuration': config, 'revisionReason': reason})


def participant(hr, path, employee_id):
    return hr.call('POST', path + '/participants', {'employeeId': employee_id, 'weightPercent': 100}, expected=(200, 201))


def reviewers(hr, participant_id, assignments):
    return hr.call('PUT', BASE + '/participants/' + participant_id + '/reviewers', {'reviewers': assignments})


def open_program(hr, path):
    hr.call('POST', path + '/common-items:apply', {})
    hr.call('POST', path + '/open', {})


def start(hr, path, stage, participant_ids):
    response = hr.call('POST', path + '/stages/' + stage + ':start',
                       {'participantIds': participant_ids, 'reason': '독립 계산정책 검증'})
    check(f'{stage} starts every selected participant without blockers',
          set(response['changedParticipantIds']) == set(participant_ids) and not response['excluded'], response)


def complete_review(actor, participant_id, round_number, scale_code):
    participant_path = BASE + '/participants/' + participant_id
    context = actor.call('GET', participant_path + f'/review-context?round={round_number}')
    check(f'Round {round_number} exposes exactly one frozen policy item', len(context['items']) == 1, context['items'])
    item = context['items'][0]
    return actor.call('POST', participant_path + f'/review-submission:complete?round={round_number}',
                      {'answers': [{'itemId': item['id'], 'scaleCode': scale_code, 'numericScore': None,
                                    'opinion': '독립 정책 증빙'}], 'overallOpinion': '독립 계산 검증'})


def calculate(hr, path, participant_ids):
    start(hr, path, 'CALCULATION', participant_ids)
    return hr.call('POST', path + '/calculations',
                   {'participantIds': participant_ids, 'excludeIncomplete': False, 'reason': '독립 수치 산출'})


def verify_configuration_guards(hr, stamp):
    program, path = create(hr, '설정 배타 검증 ' + stamp)
    original = program['configuration']
    invalid = json.loads(json.dumps(original))
    invalid['goalMode'] = 'SELF_REPORT'
    hr.call('PUT', path + '/definition', {'configuration': invalid, 'revisionReason': '배타 위반'}, expected=422,
            label='SELF_REPORT and INTERMEDIATE enabled are rejected together')
    persisted = hr.call('GET', path)
    check('Rejected self-report configuration leaves stored definition unchanged',
          persisted['configuration']['goalMode'] == 'AGREEMENT' and persisted['definitionRevision'] == program['definitionRevision'])

    missing_department_scale = json.loads(json.dumps(original))
    missing_department_scale['calculation']['departmentPerformanceEnabled'] = True
    missing_department_scale['calculation']['departmentResultScaleId'] = None
    hr.call('PUT', path + '/definition', {'configuration': missing_department_scale, 'revisionReason': '부서척도 누락'},
            expected=422, label='Department contribution requires a referenced department result scale')

    unknown_allocation_grade = json.loads(json.dumps(original))
    unknown_allocation_grade['groups'][0]['evaluationMethod'] = 'RELATIVE'
    unknown_allocation_grade['allocationRows'] = [{'populationSize': 3, 'gradeHeadcounts': {'UNKNOWN': 3}}]
    hr.call('PUT', path + '/definition', {'configuration': unknown_allocation_grade, 'revisionReason': '미등록 등급 배분'},
            expected=422, label='Allocation rows reject grade keys outside the result scale')
    return program['id']


def verify_grade_conversion_and_boundaries(hr, reviewer, employees, stamp):
    program, path = create(hr, 'GRADE 환산 경계 검증 ' + stamp)
    config = program['configuration']; group = calculation_only(config)
    input_id = config['calculation']['inputScaleId']
    input_scale = next(scale for scale in config['scales'] if scale['id'] == input_id)
    input_scale['kind'] = 'GRADE'
    input_scale['levels'] = [
        {'code': 'AT_85', 'label': '85 경계', 'convertedScore': 85, 'lowerExclusive': None, 'upperInclusive': None, 'color': None},
        {'code': 'AT_75', 'label': '75 경계', 'convertedScore': 75, 'lowerExclusive': None, 'upperInclusive': None, 'color': None},
    ]
    group['evaluationMethod'] = 'ABSOLUTE'
    group['reviewerWeightPlans'] = [{'actualReviewerCount': 1, 'reviewerWeights': {'1': 100}, 'departmentWeight': 0}]
    set_one_item_per_round(config, group['id'], input_id, [1])
    stored = configure(hr, path, config, 'GRADE 환산과 구간 경계')
    stored_input = next(scale for scale in stored['configuration']['scales'] if scale['id'] == input_id)
    check('GRADE input scale persists explicit conversion scores',
          stored_input['kind'] == 'GRADE' and {x['code']: x['convertedScore'] for x in stored_input['levels']} == {'AT_85': 85, 'AT_75': 75}, stored_input)

    participants = [participant(hr, path, employee_id) for employee_id in employees[:2]]
    assignment = lambda: [{'employeeId': reviewer.me['employeeId'], 'role': 'REVIEWER', 'round': 1, 'weightPercent': 100}]
    for row in participants:
        reviewers(hr, row['id'], assignment())
    open_program(hr, path)
    ids = [row['id'] for row in participants]
    start(hr, path, 'REVIEW', ids)
    context = reviewer.call('GET', BASE + '/participants/' + ids[0] + '/review-context?round=1')
    item_id = context['items'][0]['id']
    for code in (None, 'AT_85'):
        reviewer.call('PUT', BASE + '/participants/' + ids[0] + '/review-submission?round=1',
                      {'answers': [{'itemId': item_id, 'scaleCode': code, 'numericScore': 97, 'opinion': '우회 시도'}], 'overallOpinion': '검증'},
                      expected=422, label='Configured GRADE rejects numeric bypass and ambiguous dual input')
    complete_review(reviewer, ids[0], 1, 'AT_85')
    complete_review(reviewer, ids[1], 1, 'AT_75')
    calculations = calculate(hr, path, ids)
    by_id = {row['participantId']: row for row in calculations}
    evidence = {pid: {'raw': by_id[pid]['rawScore'], 'grade': by_id[pid]['calculatedGrade']} for pid in ids}
    check('GRADE conversion drives independent raw scores 85 and 75',
          by_id[ids[0]]['rawScore'] == 85 and by_id[ids[1]]['rawScore'] == 75, evidence)
    check('(lower, upper] result boundaries classify 85 as B and 75 as C',
          by_id[ids[0]]['calculatedGrade'] == 'B' and by_id[ids[1]]['calculatedGrade'] == 'C', evidence)
    return program['id']


def verify_three_reviewers_and_department(hr, actors, employee_id, department_id, stamp):
    program, path = create(hr, '3차 부서반영 검증 ' + stamp)
    config = program['configuration']; group = calculation_only(config)
    input_id = config['calculation']['inputScaleId']; department_scale_id = str(uuid.uuid4())
    group['evaluationMethod'] = 'ABSOLUTE'
    group['reviewerWeightPlans'] = [{'actualReviewerCount': 3,
        'reviewerWeights': {'1': 20, '2': 30, '3': 40}, 'departmentWeight': 10}]
    config['calculation']['departmentPerformanceEnabled'] = True
    config['calculation']['departmentResultScaleId'] = department_scale_id
    config['scales'].append({'id': department_scale_id, 'name': '부서 성과 척도', 'use': 'DEPARTMENT_RESULT', 'kind': 'GRADE',
        'levels': [{'code': 'A', 'label': 'A', 'convertedScore': 90, 'lowerExclusive': 80,
                    'upperInclusive': 100, 'color': None}]})
    config['departmentPerformanceGroups'] = [{'id': str(uuid.uuid4()), 'name': '검증 부서', 'grade': 'A', 'displayOrder': 0,
        'conditions': [{'field': 'ORG_UNIT', 'operator': 'EQUALS', 'values': [department_id]}]}]
    set_one_item_per_round(config, group['id'], input_id, [1, 2, 3])
    configure(hr, path, config, '실제 3차와 부서 10퍼센트')
    row = hr.call('POST', path + '/participants',
                  {'employeeId': employee_id, 'orgUnitId': department_id, 'weightPercent': 100}, expected=(200, 201)); pid = row['id']
    reviewer_rows = [{'employeeId': actor.me['employeeId'], 'role': 'REVIEWER', 'round': index,
                      'weightPercent': weight} for index, (actor, weight) in enumerate(zip(actors, (20, 30, 40)), 1)]
    reviewers(hr, pid, reviewer_rows)
    open_program(hr, path); start(hr, path, 'REVIEW', [pid])
    complete_review(actors[0], pid, 1, '1')
    hr.call('POST', path + '/calculations',
            {'participantIds': [pid], 'excludeIncomplete': False, 'reason': '1차만 완료한 조기 계산 시도'},
            expected=422, label='Three-reviewer participant cannot be calculated after only round one')
    check('Rejected premature calculation leaves no calculation revision',
          hr.call('GET', BASE + '/participants/' + pid + '/calculations') == [])
    for index, (actor, scale_code) in enumerate(zip(actors[1:], ('3', '5')), 2):
        complete_review(actor, pid, index, scale_code)
    result = calculate(hr, path, [pid])[0]
    components = {row['component']: {'score': row['score'], 'weight': row['weightPercent']} for row in result['contributions']}
    check('Three actual reviewer rounds retain configured 20/30/40 weights',
          [components[f'REVIEWER_{i}']['weight'] for i in (1, 2, 3)] == [20, 30, 40], components)
    check('Department grade A contributes converted score 90 at weight 10',
          components['DEPARTMENT_PERFORMANCE'] == {'score': 90, 'weight': 10}, components)
    check('Weighted result equals (20×20 + 60×30 + 100×40 + 90×10) / 100 = 71',
          result['rawScore'] == 71 and result['calculatedGrade'] == 'C', result)
    return program['id']


def setup_single_round_program(hr, name, config_mutator, employee_ids, reviewer_id):
    program, path = create(hr, name)
    config = program['configuration']; group = calculation_only(config); config_mutator(config, group)
    set_one_item_per_round(config, group['id'], config['calculation']['inputScaleId'], [1])
    configure(hr, path, config, name)
    rows = [participant(hr, path, employee_id) for employee_id in employee_ids]
    for row in rows:
        reviewers(hr, row['id'], [{'employeeId': reviewer_id, 'role': 'REVIEWER', 'round': 1, 'weightPercent': 100}])
    open_program(hr, path); start(hr, path, 'REVIEW', [row['id'] for row in rows])
    return program, path, rows


def verify_zero_deviation(hr, reviewer, employee_ids, stamp):
    def mutate(config, group):
        group['evaluationMethod'] = 'ABSOLUTE'
        group['reviewerWeightPlans'] = [{'actualReviewerCount': 1, 'reviewerWeights': {'1': 100}, 'departmentWeight': 0}]
        config['calculation']['adjustmentMethod'] = 'STANDARD_DEVIATION'
        config['calculation']['adjustmentTarget'] = 'ALL'
        config['calculation']['targetMean'] = 70
        config['calculation']['targetStandardDeviation'] = 10
    program, path, rows = setup_single_round_program(hr, '표준편차 0 검증 ' + stamp, mutate, employee_ids[:2], reviewer.me['employeeId'])
    for row in rows:
        complete_review(reviewer, row['id'], 1, '4')
    calculations = calculate(hr, path, [row['id'] for row in rows])
    evidence = [{'raw': row['rawScore'], 'normalized': row['normalizedScore'], 'adjusted': row['adjustedScore'],
                 'warnings': row['warnings']} for row in calculations]
    check('Zero standard deviation preserves the explainable raw score instead of dividing by zero',
          all(row['rawScore'] == 80 and row['normalizedScore'] == 80 and row['adjustedScore'] == 80 for row in calculations), evidence)
    check('Zero standard deviation is explicit in every calculation warning',
          all('ZERO_STANDARD_DEVIATION' in row['warnings'] for row in calculations), evidence)
    return program['id']


def verify_department_normalization(hr, reviewer, employee_ids, stamp):
    def mutate(config, group):
        group['evaluationMethod'] = 'ABSOLUTE'
        group['reviewerWeightPlans'] = [{'actualReviewerCount': 1, 'reviewerWeights': {'1': 100}, 'departmentWeight': 0}]
        config['calculation'].update({'populationBasis': 'DEPARTMENT', 'adjustmentMethod': 'MEAN',
                                      'adjustmentTarget': 'ALL', 'targetMean': 70})
    program, path, rows = setup_single_round_program(hr, '부서별 독립 평균보정 ' + stamp, mutate, employee_ids, reviewer.me['employeeId'])
    raw = dict(zip([row['id'] for row in rows], (40, 60, 100)))
    populations = {}
    for row, code in zip(rows, ('2', '3', '5')):
        populations.setdefault(row['employee']['orgUnitId'], []).append(row['id'])
        complete_review(reviewer, row['id'], 1, code)
    check('Normalization fixture spans at least two distinct departments', len(populations) >= 2)
    expected = {pid: raw[pid] + 70 - sum(raw[x] for x in ids) / len(ids)
                for ids in populations.values() for pid in ids}
    calculations = calculate(hr, path, [row['id'] for row in rows])
    actual = {row['participantId']: row['normalizedScore'] for row in calculations}
    check('Each department is normalized against its own mean, not the selected batch mean', actual == expected,
          {'expected': expected, 'actual': actual, 'populations': populations})
    return program['id']


def verify_exact_population_allocation(hr, reviewer, employee_ids, stamp):
    def mutate(config, group):
        group['evaluationMethod'] = 'RELATIVE'
        group['reviewerWeightPlans'] = [{'actualReviewerCount': 1, 'reviewerWeights': {'1': 100}, 'departmentWeight': 0}]
        config['calculation']['populationBasis'] = 'DEPARTMENT_PERFORMANCE_GROUP'
        config['departmentPerformanceGroups'] = [{'id': str(uuid.uuid4()), 'name': '단일 모집단', 'grade': 'A',
                                                   'displayOrder': 0, 'conditions': []}]
        config['allocationRows'] = [{'populationSize': 3, 'gradeHeadcounts': {'S': 1, 'A': 1, 'B': 1, 'C': 0, 'D': 0}}]
    program, path, rows = setup_single_round_program(hr, 'N인원 배분 검증 ' + stamp, mutate, employee_ids[:3], reviewer.me['employeeId'])
    ids = [row['id'] for row in rows]
    for row, code in zip(rows, ('5', '4', '3')):
        complete_review(reviewer, row['id'], 1, code)
    calculations = calculate(hr, path, ids)
    grade_by_raw = {row['rawScore']: row['calculatedGrade'] for row in calculations}
    check('Exact N=3 allocation assigns one S, one A, and one B in score order',
          grade_by_raw == {100: 'S', 80: 'A', 60: 'B'}, grade_by_raw)
    return program['id']


def run():
    hr = Actor('hr-admin'); employee = Actor('employee'); manager = Actor('manager')
    director = Actor('director'); colleague = Actor('colleague')
    employee_ids = [employee.me['employeeId'], colleague.me['employeeId'], manager.me['employeeId']]
    employee_option = next(row for row in hr.call('GET', RESOURCE + '/lookup/employees') if row['id'] == employee.me['employeeId'])
    check('Department condition fixture has a concrete department', bool(employee_option['departmentId']), employee_option)
    stamp = dt.datetime.now().strftime('%Y%m%d%H%M%S')
    return {
        'guardProgramId': verify_configuration_guards(hr, stamp),
        'gradeBoundaryProgramId': verify_grade_conversion_and_boundaries(hr, director, employee_ids, stamp),
        'threeReviewerDepartmentProgramId': verify_three_reviewers_and_department(
            hr, [manager, director, colleague], employee.me['employeeId'], employee_option['departmentId'], stamp),
        'zeroDeviationProgramId': verify_zero_deviation(hr, director, employee_ids, stamp),
        'departmentNormalizationProgramId': verify_department_normalization(hr, colleague, [employee.me['employeeId'], manager.me['employeeId'], hr.me['employeeId']], stamp),
        'allocationProgramId': verify_exact_population_allocation(hr, director, employee_ids, stamp),
    }


try:
    fixtures = run()
except Exception as error:
    pathlib.Path(args.output).write_text(json.dumps({'passed': False, 'error': str(error), 'checks': results}, ensure_ascii=False, indent=2))
    raise
else:
    pathlib.Path(args.output).write_text(json.dumps({'passed': True, 'count': len(results), 'fixtures': fixtures,
                                                    'checks': results}, ensure_ascii=False, indent=2))
    print(f'Program calculation policy HTTP acceptance: {len(results)} checks passed')
