$ErrorActionPreference = 'Stop'
$pidFile = Join-Path $PSScriptRoot 'companion\running.json'
if (-not (Test-Path $pidFile)) { Write-Host 'No companion started by this package is recorded.'; exit 0 }
$running = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
$proc = Get-Process -Id $running.processId -ErrorAction SilentlyContinue
if ($proc -and $proc.StartTime.ToUniversalTime().Ticks -eq [long]$running.startTicks) {
    $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($running.processId)"
    if ($details.CommandLine -notlike ('*' + $running.server + '*')) { throw 'Process identity changed; refusing to stop it.' }
    Stop-Process -Id $proc.Id
    Write-Host 'Companion stopped. Paired clients will stop and disconnect when the heartbeat expires.'
}
Remove-Item -LiteralPath $pidFile
