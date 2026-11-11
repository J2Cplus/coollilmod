$ErrorActionPreference = 'Stop'
# Preserve unrelated launcher and world settings when upgrading the original package.
& (Join-Path $PSScriptRoot 'stop-companion.ps1')
$configFile = Join-Path $PSScriptRoot 'companion\config.json'
if (-not (Test-Path $configFile)) { Copy-Item (Join-Path $PSScriptRoot 'companion\config.example.json') $configFile }
$config = Get-Content -LiteralPath $configFile -Raw | ConvertFrom-Json
$settings = @{
    provider = 'huggingface'
    providerUrl = 'https://router.huggingface.co/v1/chat/completions'
    modelId = 'Qwen/Qwen3.6-35B-A3B'
    modelLabel = 'Luna - Hugging Face Qwen'
    apiKeyEnv = 'HF_TOKEN'
    maxOutputTokens = 8192
}
foreach ($name in $settings.Keys) { $config | Add-Member -NotePropertyName $name -NotePropertyValue $settings[$name] -Force }
# UTF-8 without BOM also works with Node JSON.parse on Windows PowerShell 5.1.
[IO.File]::WriteAllText($configFile, ($config | ConvertTo-Json -Depth 20), (New-Object Text.UTF8Encoding($false)))
Write-Host 'Create a Hugging Face token at https://huggingface.co/settings/tokens with Inference Providers permission.'
Write-Host 'Hosted inference uses your Hugging Face credits and may require billing.'
& (Join-Path $PSScriptRoot 'start-companion.ps1') -PromptForApiKey
