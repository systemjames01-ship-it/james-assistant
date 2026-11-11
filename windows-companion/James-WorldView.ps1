param([string]$RequestUri)
$ErrorActionPreference = "Stop"
$base = Join-Path $env:LOCALAPPDATA "James"
$repo = Join-Path $base "GodsEyeView"
$logDir = Join-Path $base "Logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir "world-view.log"
function Write-Log([string]$Message) { Add-Content -Path $log -Value ("[{0}] {1}" -f (Get-Date -Format s),$Message) }
function Refresh-Path {
 $machine=[Environment]::GetEnvironmentVariable("Path","Machine")
 $user=[Environment]::GetEnvironmentVariable("Path","User")
 $env:Path="$machine;$user;$env:ProgramFiles\nodejs;$env:LOCALAPPDATA\Programs\Git\cmd;$env:ProgramFiles\Git\cmd"
}
function Ensure-Command([string]$Name,[string]$WingetId) {
 Refresh-Path
 if(Get-Command $Name -ErrorAction SilentlyContinue){return}
 if(-not(Get-Command winget -ErrorAction SilentlyContinue)){throw "winget não está disponível. Instale o App Installer da Microsoft e tente novamente."}
 Write-Log "Instalando dependência $Name ($WingetId)"
 winget install --id $WingetId --exact --silent --accept-package-agreements --accept-source-agreements
 Refresh-Path
 if(-not(Get-Command $Name -ErrorAction SilentlyContinue)){throw "$Name foi solicitado ao winget, mas ainda não está disponível. Feche e reabra a sessão do Windows e tente novamente."}
}
function Stop-Gev {
 $listeners=Get-NetTCPConnection -LocalPort 4173 -State Listen -ErrorAction SilentlyContinue
 foreach($listener in $listeners){
  $proc=Get-CimInstance Win32_Process -Filter ("ProcessId = {0}" -f $listener.OwningProcess) -ErrorAction SilentlyContinue
  if($proc -and $proc.CommandLine -and $proc.CommandLine.Contains($repo)){
   Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
   Write-Log "Processo local encerrado."
  }
 }
}
function Start-Gev {
 if(-not(Test-Path (Join-Path $repo "package.json"))){throw "God's Eye View ainda não está instalado. Use a ação instalar."}
 Stop-Gev
 $cmd="Set-Location -LiteralPath '$repo'; npm run dev -- --host 127.0.0.1"
 Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoProfile","-NoExit","-Command",$cmd) -WindowStyle Minimized
 Start-Sleep -Seconds 5
 Start-Process "http://127.0.0.1:4173"
 Write-Log "God's Eye View iniciado no navegador local."
}
try {
 $uri=[Uri]$RequestUri
 if($uri.Scheme -ne "james" -or $uri.Host -ne "world"){throw "Protocolo ou destino inválido."}
 $action=$uri.AbsolutePath.Trim('/').ToLowerInvariant()
 switch($action){
  "install" {
   Ensure-Command "git" "Git.Git"
   Ensure-Command "node" "OpenJS.NodeJS.LTS"
   $version=(node --version)
   if($version -notmatch '^v(24|26)\.') {throw "God's Eye View requer Node.js 24.14+ ou 26.x. Versão encontrada: $version"}
   if(-not(Test-Path (Join-Path $repo ".git"))){
    New-Item -ItemType Directory -Force -Path $base | Out-Null
    git clone --depth 1 https://github.com/bilawalsidhu/gods-eye-view.git $repo
   } else {git -C $repo pull --ff-only}
   Push-Location $repo
   try {npm ci; npm run doctor} finally {Pop-Location}
   Start-Gev
  }
  "open" {
   if(-not(Test-Path (Join-Path $repo "package.json"))){Write-Log "Instalação ausente; iniciando instalação inicial."; & $PSCommandPath "james://world/install"}
   else {Ensure-Command "node" "OpenJS.NodeJS.LTS";Start-Gev}
  }
  "update" {
   if(-not(Test-Path (Join-Path $repo ".git"))){throw "Projeto ainda não instalado. Use a ação instalar."}
   Ensure-Command "git" "Git.Git";Ensure-Command "node" "OpenJS.NodeJS.LTS"
   git -C $repo pull --ff-only
   Push-Location $repo;try {npm ci;npm run doctor} finally {Pop-Location}
   Start-Gev
  }
  "stop" {Stop-Gev;Write-Log "Solicitação de encerramento processada."}
  default {throw "Ação desconhecida. Use install, open, update ou stop."}
 }
} catch {
 Write-Log ("ERRO: "+$_.Exception.Message)
 Add-Type -AssemblyName PresentationFramework
 [System.Windows.MessageBox]::Show($_.Exception.Message,"Jâmes — Integração Windows","OK","Error") | Out-Null
}
