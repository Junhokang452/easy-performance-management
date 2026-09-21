"""Read-only server preflight. Print configuration keys and safe flags, never secrets."""
import json
import pathlib
import subprocess
import urllib.parse

base = pathlib.Path('/opt/easy-suite')
safe_keys = {'SPRING_PROFILES_ACTIVE','EASYWARE_NEON_MULTITENANCY_ENABLED','EASYPLATFORM_PERFORMANCE_STAGE2_ENABLED','EASYPLATFORM_TENANTBOOTSTRAP_ENABLED','EASYPLATFORM_TENANTCTX_ENABLED','EASYPLATFORM_AUDIT_ENABLED','EASYPLATFORM_ERROR_ENABLED','PERFORMANCE_AUTH_SEED_DEV_ACCOUNTS','PERFORMANCE_AUTH_COOKIE_SECURE','APP_TENANCY_DEFAULT_TENANT_CODE','APP_TENANCY_DEFAULT_TENANT_ID','PORT','SERVER_PORT'}
for name in ['performance.env']:
    p = base/'secrets'/name
    rows = []
    for line in p.read_text().splitlines():
        if not line or line.lstrip().startswith('#') or '=' not in line:
            continue
        key,value = line.split('=',1)
        key,value = key.strip(),value.strip().strip('"').strip("'")
        row = {'key':key,'configured':bool(value),'placeholder':any(word in value.upper() for word in ['REPLACE_','CHANGE_ME','TODO','YOUR_'])}
        if key in safe_keys:
            row['value']=value
        if key.endswith('DB_URL') or key == 'SPRING_DATASOURCE_URL':
            u=urllib.parse.urlsplit(value[5:] if value.startswith('jdbc:') else value)
            row['databaseHost']=u.hostname
            row['databaseName']=u.path
        rows.append(row)
    print(json.dumps({'file':name,'keys':rows}))
print('PERFORMANCE_SOURCE')
repo=base/'src'/'easy-performance-management'
print(json.dumps({'exists':repo.exists(),'git':(repo/'.git').exists()}))
print('EDGE_MOUNTS')
r=subprocess.run(['docker','inspect','easy-suite-lab-edge-1','--format','{{json .Mounts}}'],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True)
print(r.stdout)
print('PERFORMANCE_CONTAINERS')
subprocess.run(['docker','ps','-a','--filter','name=performance','--format','{{.Names}} {{.Status}} {{.Image}}'],check=True)
