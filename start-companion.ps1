param([switch]$PromptForApiKey)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$node = Join-Path $root 'runtime\node.exe'
if (-not (Test-Path $node)) { $node = (Get-Command node -ErrorAction Stop).Source }
$server = Join-Path $root 'companion\server.mjs'
$configFile = Join-Path $root 'companion\config.json'
if (-not (Test-Path $configFile)) { Copy-Item (Join-Path $root 'companion\config.example.json') $configFile }
$config = Get-Content -LiteralPath $configFile -Raw | ConvertFrom-Json
$port = [int]$config.controlPort
if ($port -lt 1024 -or $port -gt 65535) { throw 'controlPort must be 1024..65535.' }
$pidFile = Join-Path $root 'companion\running.json'
if (Test-Path $pidFile) {
    $running = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
    $existing = Get-Process -Id $running.processId -ErrorAction SilentlyContinue
    if ($existing -and $existing.StartTime.ToUniversalTime().Ticks -eq [long]$running.startTicks) {
        Write-Host "Companion is already running (PID $($running.processId))."
        exit 0
    }
}
$keyName = [string]$config.apiKeyEnv
if ($keyName -and $keyName -notmatch '^[A-Z][A-Z0-9_]{0,99}$') { throw 'Invalid API key environment variable name.' }
$previousKey = if ($keyName) { [Environment]::GetEnvironmentVariable($keyName, 'Process') } else { $null }
try {
    if ($PromptForApiKey -and -not $keyName) { throw 'Local Ollama needs no API key. Run without -PromptForApiKey.' }
    if ($PromptForApiKey) {
        $secure = Read-Host "API key for $keyName (kept in this process only)" -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { [Environment]::SetEnvironmentVariable($keyName, [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr), 'Process') }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    }
    $logDir = Join-Path $root 'companion\logs'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    # Quote the single script argument because Windows paths can contain spaces.
    $proc = Start-Process -FilePath $node -ArgumentList @(('"' + $server + '"')) -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'companion.log') -RedirectStandardError (Join-Path $logDir 'companion-error.log')
    $proc.Refresh()
    @{ processId = $proc.Id; startTicks = $proc.StartTime.ToUniversalTime().Ticks; server = $server } | ConvertTo-Json | Set-Content -LiteralPath $pidFile
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        Start-Sleep -Milliseconds 250
        $proc.Refresh()
        if ($proc.HasExited) { throw "Companion exited. Read $logDir\companion-error.log (an occupied port is a common cause)." }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$port/health" -TimeoutSec 1
            if ($health.name -eq 'LunaBuilder') { Write-Host "LunaBuilder companion ready on 127.0.0.1:$port. Open Minecraft 26.3 and press B."; exit 0 }
        } catch { }
    }
    throw 'Companion did not become ready within 10 seconds. Check companion\logs.'
} finally {
    if ($PromptForApiKey) { [Environment]::SetEnvironmentVariable($keyName, $previousKey, 'Process') }
}
