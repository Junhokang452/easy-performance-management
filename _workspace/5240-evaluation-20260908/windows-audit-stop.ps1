$ErrorActionPreference = 'Stop'
$workspace = '\\wsl.localhost\Ubuntu\home\samsung\code\easy-performance-management\_workspace\5240-evaluation-20260908'
$validationRoot = (Get-Content -Raw (Join-Path $workspace 'windows-validation-temp.txt')).Trim()
$runtime = Get-Content -Raw (Join-Path $workspace 'windows-audit-held-runtime.json') | ConvertFrom-Json
$pgData = [string] $runtime.pgData
if (-not ([IO.Path]::GetFullPath($pgData)).StartsWith(
        [IO.Path]::GetFullPath($validationRoot), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing to stop PostgreSQL outside the validation root'
}
$java = Get-Process -Id ([int] $runtime.javaPid) -ErrorAction SilentlyContinue
if ($java -and $java.Path.StartsWith($validationRoot, [StringComparison]::OrdinalIgnoreCase)) {
    Stop-Process -Id $java.Id
    $java.WaitForExit(15000)
}
$startInfo = [Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = 'C:\Program Files\PostgreSQL\18\bin\pg_ctl.exe'
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
foreach ($argument in @('-D', $pgData, '-m', 'fast', '-w', '-t', '60', 'stop')) {
    [void] $startInfo.ArgumentList.Add($argument)
}
$stop = [Diagnostics.Process]::new()
$stop.StartInfo = $startInfo
[void] $stop.Start()
$stop.WaitForExit()
$released = -not (Get-NetTCPConnection -State Listen -LocalPort 55489, 8089 -ErrorAction SilentlyContinue)
@{
    stoppedAt = (Get-Date -Format o)
    javaStopped = -not (Get-Process -Id ([int] $runtime.javaPid) -ErrorAction SilentlyContinue)
    pgStopExitCode = $stop.ExitCode
    portsReleased = $released
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $workspace 'windows-audit-held-stop.json') -Encoding utf8NoBOM
if (-not $released -or $stop.ExitCode -ne 0) { exit 1 }
