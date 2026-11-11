param([string]$ModsFolder = (Join-Path $env:APPDATA '.minecraft\mods'))
$ErrorActionPreference = 'Stop'
$bundled = Join-Path $PSScriptRoot 'mods'
if (-not (Test-Path $bundled)) { throw 'Use the Windows bundle, or copy build\libs\lunabuilder-1.0.0.jar and Fabric API manually.' }
New-Item -ItemType Directory -Force -Path $ModsFolder | Out-Null
$api = Get-ChildItem -LiteralPath $bundled -Filter 'fabric-api-*.jar' | Select-Object -First 1
$conflicts = @(Get-ChildItem -LiteralPath $ModsFolder -Filter 'fabric-api-*.jar' | Where-Object Name -ne $api.Name)
if ($conflicts.Count) { throw 'Another Fabric API JAR is present. Move the older Fabric API out of this mods folder, then retry.' }
Get-ChildItem -LiteralPath $bundled -Filter '*.jar' | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $ModsFolder -Force }
Write-Host "Installed LunaBuilder and Fabric API into $ModsFolder. Select Fabric Loader 0.20.1 for Minecraft 26.3 in your launcher."
