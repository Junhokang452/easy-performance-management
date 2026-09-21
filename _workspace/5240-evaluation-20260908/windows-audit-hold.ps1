$ErrorActionPreference = 'Stop'

$sourceRoot = '\\wsl.localhost\Ubuntu\home\samsung\code\easy-performance-management'
$workspace = Join-Path $sourceRoot '_workspace\5240-evaluation-20260908'
$validationRoot = (Get-Content -Raw (Join-Path $workspace 'windows-validation-temp.txt')).Trim()
$backendCopy = Join-Path $validationRoot 'easy-performance-management\backend'
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$pgData = Get-ChildItem $validationRoot -Directory -Filter 'pg18-audit-55489-*' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$pgLog = Join-Path $validationRoot 'windows-audit-held-postgres.log'
$backendOut = Join-Path $validationRoot 'windows-audit-held-backend.stdout.log'
$backendErr = Join-Path $validationRoot 'windows-audit-held-backend.stderr.log'
$lifecycleLog = Join-Path $validationRoot 'windows-audit-held-lifecycle.log'
$verifierLog = Join-Path $validationRoot 'windows-audit-held-verifier.log'
$verifierJson = Join-Path $validationRoot 'windows-audit-held-api.json'
$openApiJson = Join-Path $validationRoot 'windows-audit-held-openapi.json'
$runtimeJson = Join-Path $validationRoot 'windows-audit-held-runtime.json'
$javaProcess = $null
$pgStarted = $false
$keepAlive = $false

function Note([string]$message) {
    "$(Get-Date -Format o) $message" | Tee-Object -FilePath $lifecycleLog -Append
}

function Invoke-ProcessOnly([string]$fileName, [string[]]$arguments) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $fileName
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    foreach ($argument in $arguments) { [void] $startInfo.ArgumentList.Add($argument) }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw "Could not start $fileName" }
    $process.WaitForExit()
    return $process.ExitCode
}

try {
    if (-not $pgData -or -not ([IO.Path]::GetFullPath($pgData)).StartsWith(
            [IO.Path]::GetFullPath($validationRoot), [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Dedicated PostgreSQL data directory is outside the validation root'
    }
    if (Get-NetTCPConnection -State Listen -LocalPort 55489, 8089 -ErrorAction SilentlyContinue) {
        throw 'Port 55489 or 8089 is already in use'
    }
    Note "PG_DATA=$pgData"
    $pgStartCode = Invoke-ProcessOnly (Join-Path $pgBin 'pg_ctl.exe') `
        @('-D', $pgData, '-l', $pgLog, '-o', '-p 55489 -h 127.0.0.1', '-w', '-t', '60', 'start')
    Note "PG_START_EXIT_CODE=$pgStartCode"
    if ($pgStartCode -ne 0) { throw "pg_ctl start failed: $pgStartCode" }
    $pgStarted = $true

    $javaExe = Get-ChildItem (Join-Path $validationRoot 'jdk21') -Recurse -Filter java.exe -File |
        Where-Object FullName -like '*\bin\java.exe' | Select-Object -First 1 -ExpandProperty FullName
    $jar = Get-ChildItem (Join-Path $backendCopy 'build\libs') -Filter '*.jar' -File |
        Where-Object Name -notlike '*-plain.jar' | Select-Object -First 1 -ExpandProperty FullName
    if (-not $javaExe -or -not $jar) { throw 'Portable Java or bootJar missing' }

    $env:PERFORMANCE_DEMO_DB_PASSWORD = 'temporary-trust-placeholder'
    $env:PERFORMANCE_DEMO_JWT_SECRET = [Convert]::ToHexString(
        [Security.Cryptography.RandomNumberGenerator]::GetBytes(64)).ToLowerInvariant()
    $javaArgs = @(
        '-Xms128m', '-Xmx512m', '-jar', $jar,
        '--spring.profiles.active=local-demo', '--server.address=127.0.0.1', '--server.port=8089',
        '--spring.datasource.url=jdbc:postgresql://127.0.0.1:55489/performance_demo',
        '--spring.datasource.username=performance_demo', '--spring.datasource.password=',
        '--easyware.neon.multitenancy-enabled=false', '--easyplatform.tenantbootstrap.enabled=false',
        '--easyplatform.performance.stage2.enabled=false', '--performance.s2s.easytalent.base-url=',
        '--performance.s2s.hcm.bearer-token=', '--performance.s2s.hcm.hmac-secret='
    )
    $javaProcess = Start-Process -FilePath $javaExe -ArgumentList $javaArgs `
        -RedirectStandardOutput $backendOut -RedirectStandardError $backendErr `
        -PassThru -WindowStyle Hidden
    Note "JAVA_PID=$($javaProcess.Id)"

    $ready = $false
    for ($attempt = 1; $attempt -le 180; $attempt++) {
        if ($javaProcess.HasExited) { break }
        try {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8089/actuator/health' -TimeoutSec 2
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch {}
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Backend did not reach health UP' }
    Note 'HEALTH=UP'

    $browserSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $headers = @{ 'X-Requested-With' = 'XMLHttpRequest' }
    $loginBody = @{ email = 'dev-hr-admin@performance.dev'; password = 'dev' } | ConvertTo-Json
    $null = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8089/api/auth/session/login' `
        -ContentType 'application/json' -Headers $headers -Body $loginBody -WebSession $browserSession
    $stamp = Get-Date -Format 'yyyyMMddHHmmss'
    $createBody = @{
        name = "Audit browser fixture $stamp"
        evaluationYear = 2026
        asOfDate = '2026-01-01'
        startsOn = '2026-01-01'
        endsOn = '2026-12-31'
        kind = 'PERFORMANCE'
    } | ConvertTo-Json
    $program = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8089/api/v1/evaluation-programs' `
        -ContentType 'application/json' -Headers $headers -Body $createBody -WebSession $browserSession
    $programId = [string] $program.id
    $participantId = [guid]::NewGuid().ToString()
    Note "UI_PROGRAM_ID=$programId"

    $fixtureSql = @"
INSERT INTO evaluation_program(id,tenant_id,name,evaluation_year,as_of_date,starts_on,ends_on,kind,status,definition_json)
VALUES ('019ef100-0000-7000-8000-000000000010','00000000-0000-0000-0000-000000000002','Tenant B audit fixture',2026,'2026-01-01','2026-01-01','2026-12-31','PERFORMANCE','DRAFT','{}'::jsonb)
ON CONFLICT (id) DO NOTHING;
INSERT INTO program_participant(id,tenant_id,program_id,employee_id,assignment_key,attributes_json,status,weight_percent,stage_status,current_round)
VALUES (
  '$participantId','00000000-0000-0000-0000-000000000001','$programId',
  '019ed002-0000-7000-8000-000000000001','browser-audit-fixture',
  jsonb_build_object(
    'employeeId','019ed002-0000-7000-8000-000000000001',
    'employeeNo','DEMO-001',
    'name','김나리',
    'assignmentId','019ed005-0000-7000-8000-000000000001',
    'orgUnitId','019ed001-0000-7000-8000-000000000001',
    'orgUnitName','제품개발팀',
    'positionCode','MEMBER',
    'gradeCode','G3',
    'jobCode','SERVICE',
    'employmentType','REGULAR'
  ),
  'ACTIVE',100,'NOT_STARTED',0
);
INSERT INTO program_audit_event(id,tenant_id,program_id,participant_id,event_type,reason,actor_employee_id,details_json,created_at,updated_at)
SELECT gen_random_uuid(),
       '00000000-0000-0000-0000-000000000001'::uuid,
       '$programId'::uuid,
       CASE WHEN gs IN (1,2) THEN NULL ELSE '$participantId'::uuid END,
       CASE WHEN gs % 2 = 0 THEN 'PROGRAM_BASIC_UPDATED' ELSE 'PARTICIPANT_CHANGED' END,
       'browser audit fixture ' || gs,
       CASE WHEN gs = 1 THEN NULL ELSE '019ed002-0000-7000-8000-000000000004'::uuid END,
       jsonb_build_object('private','must-not-leak','sequence',gs),
       timestamp '2026-09-08 12:00:00' + (gs || ' seconds')::interval,
       timestamp '2026-09-08 12:00:00' + (gs || ' seconds')::interval
FROM generate_series(1,30) gs;
"@
    & (Join-Path $pgBin 'psql.exe') -h 127.0.0.1 -p 55489 -U performance_demo `
        -d performance_demo -v ON_ERROR_STOP=1 -c $fixtureSql
    if ($LASTEXITCODE -ne 0) { throw "browser audit fixture failed: $LASTEXITCODE" }

    # Browser QA owns the remaining lifetime. Persist the exact managed targets before
    # running secondary evidence collectors so a verifier/OpenAPI timeout cannot tear down QA.
    $pgPid = (Get-NetTCPConnection -State Listen -LocalPort 55489 | Select-Object -First 1).OwningProcess
    @{
        held = $true
        baseUrl = 'http://127.0.0.1:8089'
        programId = $programId
        programName = [string] $program.name
        participantId = $participantId
        auditRowsMinimum = 31
        javaPid = $javaProcess.Id
        postgresPid = $pgPid
        pgData = $pgData
        verifier = 'windows-audit-held-api.json'
        openApi = 'windows-audit-held-openapi.json'
    } | ConvertTo-Json | Set-Content -LiteralPath $runtimeJson -Encoding utf8NoBOM
    $keepAlive = $true
    Note 'RUNTIME_HELD_FOR_BROWSER_QA=true'

    & python (Join-Path $sourceRoot 'scripts\verify-program-audit.py') `
        --base-url 'http://127.0.0.1:8089' --output $verifierJson 2>&1 |
        Tee-Object -FilePath $verifierLog
    if ($LASTEXITCODE -ne 0) { throw "audit verifier failed: $LASTEXITCODE" }
    Note 'VERIFIER_EXIT_CODE=0'

    Invoke-WebRequest -Uri 'http://127.0.0.1:8089/v3/api-docs' -TimeoutSec 180 -OutFile $openApiJson
    $null = Get-Content -Raw $openApiJson | ConvertFrom-Json
    Note 'OPENAPI_JSON=VALID'

} finally {
    if (-not $keepAlive) {
        if ($javaProcess -and -not $javaProcess.HasExited) {
            Stop-Process -Id $javaProcess.Id
            $javaProcess.WaitForExit(15000)
        }
        if ($pgStarted) {
            $null = Invoke-ProcessOnly (Join-Path $pgBin 'pg_ctl.exe') `
                @('-D', $pgData, '-m', 'fast', '-w', '-t', '60', 'stop')
        }
    }
    foreach ($artifact in @(
        @($lifecycleLog, 'windows-audit-held-lifecycle.log'),
        @($pgLog, 'windows-audit-held-postgres.log'),
        @($backendOut, 'windows-audit-held-backend.stdout.log'),
        @($backendErr, 'windows-audit-held-backend.stderr.log'),
        @($verifierLog, 'windows-audit-held-verifier.log'),
        @($verifierJson, 'windows-audit-held-api.json'),
        @($openApiJson, 'windows-audit-held-openapi.json'),
        @($runtimeJson, 'windows-audit-held-runtime.json')
    )) {
        if (Test-Path $artifact[0]) {
            Copy-Item -LiteralPath $artifact[0] -Destination (Join-Path $workspace $artifact[1]) -Force
        }
    }
}
