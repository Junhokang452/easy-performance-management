$ErrorActionPreference = 'Stop'

$sourceRoot = '\\wsl.localhost\Ubuntu\home\samsung\code\easy-performance-management'
$sourceWorkspace = Join-Path $sourceRoot '_workspace\5240-evaluation-20260908'
$validationRoot = (Get-Content -Raw (Join-Path $sourceWorkspace 'windows-validation-temp.txt')).Trim()
$backendCopy = Join-Path $validationRoot 'easy-performance-management\backend'
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$pgData = Join-Path $validationRoot ('pg18-audit-55489-' + [guid]::NewGuid().ToString('N'))
$pgLog = Join-Path $validationRoot 'windows-audit-postgres.log'
$backendOut = Join-Path $validationRoot 'windows-audit-backend.stdout.log'
$backendErr = Join-Path $validationRoot 'windows-audit-backend.stderr.log'
$lifecycleLog = Join-Path $validationRoot 'windows-audit-lifecycle.log'
$verifierLog = Join-Path $validationRoot 'windows-audit-verifier.log'
$verifierJson = Join-Path $validationRoot 'windows-audit-api.json'
$openApiJson = Join-Path $validationRoot 'windows-audit-openapi.json'
$seedCopy = Join-Path $validationRoot 'local-demo-seed.sql'
$javaProcess = $null
$pgStarted = $false
$overallCode = 1

function Note([string]$message) {
    $line = "$(Get-Date -Format o) $message"
    $line | Tee-Object -FilePath $lifecycleLog -Append
}

function Invoke-ProcessOnly([string]$fileName, [string[]]$arguments) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $fileName
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    foreach ($argument in $arguments) {
        [void] $startInfo.ArgumentList.Add($argument)
    }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw "Could not start $fileName" }
    $process.WaitForExit()
    return $process.ExitCode
}

try {
    if (Get-NetTCPConnection -State Listen -LocalPort 55489, 8089 -ErrorAction SilentlyContinue) {
        throw 'Port 55489 or 8089 is already in use'
    }
    New-Item -ItemType Directory -Path $pgData | Out-Null
    Copy-Item -LiteralPath (Join-Path $sourceRoot 'scripts\local-demo-seed.sql') -Destination $seedCopy
    Note "PG_DATA=$pgData"

    & (Join-Path $pgBin 'initdb.exe') -D $pgData --auth-host=trust --auth-local=trust `
        -U performance_demo --encoding=UTF8 --no-locale 2>&1 | Tee-Object -FilePath $lifecycleLog -Append
    if ($LASTEXITCODE -ne 0) { throw "initdb failed: $LASTEXITCODE" }

    $pgStartCode = Invoke-ProcessOnly (Join-Path $pgBin 'pg_ctl.exe') `
        @('-D', $pgData, '-l', $pgLog, '-o', '-p 55489 -h 127.0.0.1', '-w', '-t', '60', 'start')
    Note "PG_START_EXIT_CODE=$pgStartCode"
    if ($pgStartCode -ne 0) { throw "pg_ctl start failed: $pgStartCode" }
    $pgStarted = $true

    & (Join-Path $pgBin 'createdb.exe') -h 127.0.0.1 -p 55489 `
        -U performance_demo performance_demo 2>&1 | Tee-Object -FilePath $lifecycleLog -Append
    if ($LASTEXITCODE -ne 0) { throw "createdb failed: $LASTEXITCODE" }

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
        '--spring.profiles.active=local-demo',
        '--server.address=127.0.0.1', '--server.port=8089',
        '--spring.datasource.url=jdbc:postgresql://127.0.0.1:55489/performance_demo',
        '--spring.datasource.username=performance_demo', '--spring.datasource.password=',
        '--easyware.neon.multitenancy-enabled=false',
        '--easyplatform.tenantbootstrap.enabled=false',
        '--easyplatform.performance.stage2.enabled=false',
        '--performance.s2s.easytalent.base-url=',
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
    if (-not $ready) {
        $state = if ($javaProcess.HasExited) { "exit=$($javaProcess.ExitCode)" } else { 'running' }
        throw "backend not ready: $state"
    }
    Note 'HEALTH=UP'

    $seedReady = $false
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $count = & (Join-Path $pgBin 'psql.exe') -h 127.0.0.1 -p 55489 `
            -U performance_demo -d performance_demo -Atc `
            "SELECT count(DISTINCT role) FROM user_account WHERE tenant_id='00000000-0000-0000-0000-000000000001' AND email LIKE 'dev-%@performance.dev'"
        if ($count -eq '5') { $seedReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $seedReady) { throw 'dev account seeder did not create five roles' }

    & (Join-Path $pgBin 'psql.exe') -h 127.0.0.1 -p 55489 -U performance_demo `
        -d performance_demo -v ON_ERROR_STOP=1 -f $seedCopy 2>&1 | Tee-Object -FilePath $lifecycleLog -Append
    if ($LASTEXITCODE -ne 0) { throw "local demo seed failed: $LASTEXITCODE" }

    $fixtureSql = @"
INSERT INTO evaluation_program(id,tenant_id,name,evaluation_year,as_of_date,starts_on,ends_on,kind,status,definition_json)
VALUES ('019ef000-0000-7000-8000-000000000001','00000000-0000-0000-0000-000000000001','Audit HTTP fixture',2026,'2026-01-01','2026-01-01','2026-12-31','PERFORMANCE','DRAFT','{}'::jsonb);
INSERT INTO program_participant(id,tenant_id,program_id,employee_id,assignment_key,attributes_json,status,weight_percent,stage_status,current_round)
VALUES ('019ef000-0000-7000-8000-000000000002','00000000-0000-0000-0000-000000000001','019ef000-0000-7000-8000-000000000001','019ed002-0000-7000-8000-000000000001','audit-fixture','{}'::jsonb,'ACTIVE',100,'NOT_STARTED',0);
INSERT INTO program_audit_event(id,tenant_id,program_id,participant_id,event_type,reason,actor_employee_id,details_json,created_at,updated_at)
VALUES
('019ef000-0000-7000-8000-000000000003','00000000-0000-0000-0000-000000000001','019ef000-0000-7000-8000-000000000001',NULL,'PROGRAM_CREATED','system fixture',NULL,'{"private":"must-not-leak"}'::jsonb,'2026-09-08 10:00:00','2026-09-08 10:00:00'),
('019ef000-0000-7000-8000-000000000004','00000000-0000-0000-0000-000000000001','019ef000-0000-7000-8000-000000000001','019ef000-0000-7000-8000-000000000002','PARTICIPANT_CREATED','participant fixture','019ed002-0000-7000-8000-000000000004','{"private":"must-not-leak"}'::jsonb,'2026-09-08 10:01:00','2026-09-08 10:01:00');
"@
    & (Join-Path $pgBin 'psql.exe') -h 127.0.0.1 -p 55489 -U performance_demo `
        -d performance_demo -v ON_ERROR_STOP=1 -c $fixtureSql 2>&1 | Tee-Object -FilePath $lifecycleLog -Append
    if ($LASTEXITCODE -ne 0) { throw "audit fixture failed: $LASTEXITCODE" }

    & python (Join-Path $sourceRoot 'scripts\verify-program-audit.py') `
        --base-url 'http://127.0.0.1:8089' --output $verifierJson 2>&1 |
        Tee-Object -FilePath $verifierLog
    $verifierCode = $LASTEXITCODE
    Note "VERIFIER_EXIT_CODE=$verifierCode"
    if ($verifierCode -ne 0) { throw "audit verifier failed: $verifierCode" }
    Invoke-WebRequest -Uri 'http://127.0.0.1:8089/v3/api-docs' -TimeoutSec 30 -OutFile $openApiJson
    $null = Get-Content -Raw $openApiJson | ConvertFrom-Json
    Note 'OPENAPI_JSON=VALID'
    $overallCode = 0
} catch {
    Note "ERROR=$($_.Exception.Message)"
    $overallCode = 1
} finally {
    if ($javaProcess -and -not $javaProcess.HasExited) {
        Stop-Process -Id $javaProcess.Id
        $javaProcess.WaitForExit(15000)
        Note "JAVA_STOPPED=$($javaProcess.HasExited)"
    }
    if ($pgStarted) {
        $pgStopCode = Invoke-ProcessOnly (Join-Path $pgBin 'pg_ctl.exe') `
            @('-D', $pgData, '-m', 'fast', '-w', '-t', '60', 'stop')
        Note "PG_STOP_EXIT_CODE=$pgStopCode"
    }
    $ports = @(Get-NetTCPConnection -State Listen -LocalPort 55489, 8089 -ErrorAction SilentlyContinue)
    Note "PORTS_RELEASED=$($ports.Count -eq 0)"
    $artifacts = @(
        @($lifecycleLog, 'windows-audit-lifecycle.log'),
        @($pgLog, 'windows-audit-postgres.log'),
        @($backendOut, 'windows-audit-backend.stdout.log'),
        @($backendErr, 'windows-audit-backend.stderr.log'),
        @($verifierLog, 'windows-audit-verifier.log'),
        @($verifierJson, 'windows-audit-api.json'),
        @($openApiJson, 'windows-audit-openapi.json')
    )
    foreach ($artifact in $artifacts) {
        if (Test-Path $artifact[0]) {
            Copy-Item -LiteralPath $artifact[0] -Destination (Join-Path $sourceWorkspace $artifact[1]) -Force
        }
    }
}
exit $overallCode
