param([ValidateSet('start','stop')][string]$Action = 'start')
$ErrorActionPreference = 'Stop'
$s1Root = 'C:/Users/SAMSUNG/AppData/Local/Temp/easy-performance-validation-b1ce4a863a3c41ffb3bd65cfa5fe87a3'
$s1Backend = Join-Path $s1Root 'easy-performance-management/backend'
$s1Data = Join-Path $s1Root 'pg18-audit-55489-c278dbccdfc94f44b3e8943fcca965fb'
$s1PgBin = 'C:/Program Files/PostgreSQL/18/bin'
$s1Java = Join-Path $s1Root 'jdk21/jdk-21.0.12.1+1/bin/java.exe'
$s1Meta = Join-Path $s1Root 's1-runtime.json'

function ProtectedListeners {
    @(Get-NetTCPConnection -State Listen -LocalPort 5432,5433 -ErrorAction SilentlyContinue |
        Select-Object LocalPort,OwningProcess -Unique | Sort-Object LocalPort,OwningProcess)
}

if ($Action -eq 'stop') {
    $runtime = Get-Content -Raw -LiteralPath $s1Meta | ConvertFrom-Json
    if ($runtime.pgData -ne $s1Data) { throw 'Unexpected PostgreSQL target' }
    $ownedJava = Get-CimInstance Win32_Process -Filter "ProcessId=$($runtime.javaPid)"
    if ($ownedJava) {
        if ($ownedJava.ExecutablePath -ne ([IO.Path]::GetFullPath($s1Java)) -or
            $ownedJava.CommandLine -notlike '*--server.port=8089*' -or
            $ownedJava.CommandLine -notlike '*easy-performance-management*') { throw 'Java ownership mismatch' }
        Stop-Process -Id $runtime.javaPid
    }
    & "$s1PgBin/pg_ctl.exe" -D $s1Data -m fast -w -t 30 stop
    if ($LASTEXITCODE -ne 0) { throw 'Owned PostgreSQL stop failed' }
    $after = ProtectedListeners
    if (($runtime.protected | ConvertTo-Json -Compress) -ne ($after | ConvertTo-Json -Compress)) {
        throw 'Protected listener state changed; investigate, do not stop other processes'
    }
    $runtime.held = $false
    $runtime | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $s1Meta -Encoding utf8NoBOM
    Write-Output 'S1_RUNTIME_STOPPED; protected PostgreSQL listeners unchanged'
    exit
}

if (Get-NetTCPConnection -State Listen -LocalPort 55489,8089 -ErrorAction SilentlyContinue) {
    throw 'Dedicated S1 ports already occupied; no process was stopped'
}
if (-not (Test-Path -LiteralPath $s1Data) -or
    -not ([IO.Path]::GetFullPath($s1Data)).StartsWith([IO.Path]::GetFullPath($s1Root) + [IO.Path]::DirectorySeparatorChar)) {
    throw 'Dedicated cluster path mismatch'
}
$protected = ProtectedListeners
$jar = Get-ChildItem "$s1Backend/build/libs" -File -Filter '*.jar' |
    Where-Object Name -notlike '*-plain.jar' | Select-Object -First 1 -ExpandProperty FullName
if (-not $jar) { throw 'Current bootJar required' }
$ownedProcess = $null
$pgStarted = $false
$keepAlive = $false
try {
    & "$s1PgBin/pg_ctl.exe" -D $s1Data -l "$s1Root/s1-postgres.log" -o '-p 55489 -h 127.0.0.1' -w -t 60 start
    if ($LASTEXITCODE -ne 0) { throw 'Dedicated PostgreSQL startup failed' }
    $pgStarted = $true
    $env:PERFORMANCE_DEMO_DB_PASSWORD = 'temporary-trust-placeholder'
    $env:PERFORMANCE_DEMO_JWT_SECRET = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
    # Synthetic local fixture credentials only. No existing HCM/Neon/SMTP credentials loaded.
    $env:PERFORMANCE_S2S_HCM_BEARER_TOKEN = 's1-synthetic-local-token'
    $env:PERFORMANCE_S2S_HCM_HMAC_SECRET = 's1-synthetic-local-hmac-key-at-least-32-characters'
    $javaArgs = @('-Xms128m','-Xmx512m','-jar',$jar,
        '--spring.profiles.active=local-demo','--server.address=127.0.0.1','--server.port=8089',
        '--spring.datasource.url=jdbc:postgresql://127.0.0.1:55489/performance_demo',
        '--spring.datasource.username=performance_demo','--spring.datasource.password=',
        '--easyware.neon.multitenancy-enabled=false','--easyplatform.tenantbootstrap.enabled=false',
        '--easyplatform.performance.stage2.enabled=false','--performance.s2s.easytalent.base-url=',
        "--performance.s2s.hcm.bearer-token=$env:PERFORMANCE_S2S_HCM_BEARER_TOKEN",
        "--performance.s2s.hcm.hmac-secret=$env:PERFORMANCE_S2S_HCM_HMAC_SECRET",
        '--performance.s2s.hcm.tenant-id=00000000-0000-0000-0000-000000000001')
    $ownedProcess = Start-Process -FilePath $s1Java -ArgumentList $javaArgs -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput "$s1Root/s1-backend.stdout.log" -RedirectStandardError "$s1Root/s1-backend.stderr.log"
    $ready = $false
    for ($attempt=1; $attempt -le 180; $attempt++) {
        if ($ownedProcess.HasExited) { break }
        try {
            if ((Invoke-RestMethod 'http://127.0.0.1:8089/actuator/health' -TimeoutSec 2).status -eq 'UP') {
                $ready=$true; break
            }
        } catch {}
        if ($attempt % 15 -eq 0) { Write-Output "S1 health pending ($attempt)" }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'S1 backend health timeout; inspect local logs' }
    @{ held=$true; baseUrl='http://127.0.0.1:8089'; javaPid=$ownedProcess.Id; pgData=$s1Data;
       jarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash; protected=$protected } |
        ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $s1Meta -Encoding utf8NoBOM
    $keepAlive=$true
    Write-Output 'S1_RUNTIME_READY http://127.0.0.1:8089; synthetic local receiver enabled'
} finally {
    if (-not $keepAlive) {
        if ($ownedProcess -and -not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id }
        if ($pgStarted) { & "$s1PgBin/pg_ctl.exe" -D $s1Data -m fast -w -t 30 stop }
    }
}
