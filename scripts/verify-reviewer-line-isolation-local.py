#!/usr/bin/env python3
"""Real-PG cross-tenant assigned-UUID collision tests in the dedicated local fixture DB."""
import argparse
import hashlib
import hmac
import json
from pathlib import Path
import subprocess
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--output', required=True)
args = parser.parse_args()
TENANT_A = '00000000-0000-0000-0000-000000000001'
TENANT_B = '00000000-0000-0000-0000-000000000002'
PSQL = 'C:/Program Files/PostgreSQL/18/bin/psql.exe'
cases = []

def sql(statement):
    # Fixed loopback + dedicated validation port/database. No host/DB from caller input.
    return subprocess.run([PSQL, '-h', '127.0.0.1', '-p', '55489', '-U', 'performance_demo',
        '-d', 'performance_demo', '-v', 'ON_ERROR_STOP=1', '-At', '-c', statement],
        check=True, capture_output=True, text=True, encoding='utf-8').stdout.strip()

def receive(body):
    raw = json.dumps(body).encode('utf-8')
    signature = hmac.new(b's1-synthetic-local-hmac-key-at-least-32-characters', raw, hashlib.sha256).hexdigest()
    request = urllib.request.Request('http://127.0.0.1:8089/api/internal/sync/core-master', data=raw,
        headers={'Content-Type': 'application/json', 'Authorization': 'Bearer s1-synthetic-local-token',
                 'X-Tenant-Uuid': TENANT_A, 'X-Signature': signature}, method='POST')
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status
    except urllib.error.HTTPError as error:
        error.read()
        return error.code

try:
    for collection, table, extra_columns, extra_values in [
        ('employees', 'rm_employee', 'employee_no,name,status', "'B-SYNTHETIC','B preserved','ACTIVE'"),
        ('orgUnits', 'rm_org_unit', 'code,name', "'B-SYNTHETIC','B preserved'"),
        ('assignments', 'rm_assignment', 'employee_id,effective_from', f"'{uuid.uuid4()}',DATE '2026-01-01'"),
    ]:
        victim, fresh = str(uuid.uuid4()), str(uuid.uuid4())
        sql(f"INSERT INTO {table}(id,tenant_id,source_version,synced_at,{extra_columns}) VALUES ('{victim}','{TENANT_B}',1,now(),{extra_values})")
        row = {'id': victim, 'sourceVersion': 999999999999999,
               'employeeNo': 'collision', 'name': 'must not overwrite', 'status': 'ACTIVE',
               'code': 'collision', 'employeeId': str(uuid.uuid4()), 'effectiveFrom': '2026-01-01',
               'effectiveTo': None, 'deleted': False, 'managerEmployeeId': None}
        # Use exact DTO fields per collection; no unknown fields relied upon.
        allowed = {'employees': ['id','sourceVersion','employeeNo','name','status'],
                   'orgUnits': ['id','sourceVersion','code','name'],
                   'assignments': ['id','sourceVersion','employeeId','effectiveFrom','effectiveTo','deleted','managerEmployeeId']}[collection]
        row = {key: row[key] for key in allowed}
        batch = {'tenantId': TENANT_A, 'employees': [{'id': fresh, 'employeeNo': fresh,
            'name': 'must roll back', 'status': 'ACTIVE', 'sourceVersion': 2}], 'orgUnits': [], 'assignments': []}
        batch[collection].append(row)
        status = receive(batch)
        preserved = sql(f"SELECT tenant_id::text || ':' || source_version::text FROM {table} WHERE id='{victim}'") == TENANT_B + ':1'
        atomic = sql(f"SELECT count(*) FROM rm_employee WHERE id='{fresh}'") == '0'
        result = {'case': collection + ' cross-tenant PK collision preserves B and rolls back A',
                  'httpStatus': status, 'passed': status == 403 and preserved and atomic}
        cases.append(result)
        if not result['passed']:
            raise AssertionError(result['case'])
finally:
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    Path(args.output).write_text(json.dumps({'synthetic': True, 'cases': cases}, indent=2), encoding='utf-8')
    print(json.dumps(cases))
