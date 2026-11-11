$ErrorActionPreference = 'Stop'
$node = Join-Path $PSScriptRoot 'runtime\node.exe'
if (-not (Test-Path $node)) { $node = (Get-Command node -ErrorAction Stop).Source }
& $node (Join-Path $PSScriptRoot 'companion\ollama.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Ollama setup failed. Open the Ollama app and confirm at least one local chat model is installed, then retry.' }
& (Join-Path $PSScriptRoot 'stop-companion.ps1')
& (Join-Path $PSScriptRoot 'start-companion.ps1')
