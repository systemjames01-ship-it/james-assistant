# Jâmes Windows Companion installer (per-user; no administrator rights required)
$ErrorActionPreference = "Stop"
$base = Join-Path $env:LOCALAPPDATA "James\WindowsCompanion"
New-Item -ItemType Directory -Force -Path $base | Out-Null
$agent = Join-Path $base "James-WorldView.ps1"
$raw = "https://raw.githubusercontent.com/systemjames01-ship-it/james-assistant/main/windows-companion/James-WorldView.ps1"
Invoke-WebRequest -Uri $raw -OutFile $agent -UseBasicParsing
$protocol = "HKCU:\Software\Classes\james"
New-Item -Path $protocol -Force | Out-Null
Set-Item -Path $protocol -Value "URL:Jâmes Windows Companion Protocol"
New-ItemProperty -Path $protocol -Name "URL Protocol" -Value "" -PropertyType String -Force | Out-Null
$commandKey = Join-Path $protocol "shell\open\command"
New-Item -Path $commandKey -Force | Out-Null
$hostExe = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
$command = "`"$hostExe`" -NoProfile -ExecutionPolicy Bypass -File `"$agent`" `"%1`""
Set-Item -Path $commandKey -Value $command
Write-Host ""
Write-Host "Integração Windows do Jâmes registrada." -ForegroundColor Cyan
Write-Host "Agora volte ao Jâmes e use MUNDO > ABRIR NO WINDOWS." -ForegroundColor Green
Write-Host "O primeiro uso pode instalar Git, Node.js e God's Eye View."
